package com.example.minibank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class BankingApiIntegrationTest {

    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("minibank_test")
                    .withUsername("minibank_test")
                    .withPassword("minibank_test_password");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.expiration-ms", () -> "3600000");
        registry.add("app.cors.allowed-origin", () -> "http://localhost:5173");
    }

    @Test
    void accountEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/account"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanRegisterLoginDepositWithdrawAndReadHistory() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/account").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        mockMvc.perform(post("/api/account/deposit")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":1000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(1000.0));

        mockMvc.perform(post("/api/account/withdraw")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(750.0));

        mockMvc.perform(get("/api/account/transactions?page=0&size=10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void customerCannotReadAnotherCustomersAccount() throws Exception {
        String firstToken = registerAndLogin();
        String secondToken = registerAndLogin();

        mockMvc.perform(post("/api/account/deposit")
                        .header("Authorization", bearer(firstToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":90.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account").header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        mockMvc.perform(get("/api/account/transactions")
                        .header("Authorization", bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void insufficientFundsDoesNotChangeBalanceOrLedger() throws Exception {
        String token = registerAndLogin();
        deposit(token, "20.00");

        mockMvc.perform(post("/api/account/withdraw")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":20.01}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/account")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(20.0));

        mockMvc.perform(get("/api/account/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void transferMovesMoneyAndWritesPairedLedgerEntries() throws Exception {
        Customer sender = registerCustomerAndLogin();
        Customer recipient = registerCustomerAndLogin();
        deposit(sender.token(), "75.00");

        mockMvc.perform(post("/api/account/transfer")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientAccountNumber\":\"" + recipient.accountNumber()
                                + "\",\"amount\":25.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debit.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.debit.balanceAfter").value(50.0))
                .andExpect(jsonPath("$.credit.type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.credit.balanceAfter").value(25.0));

        mockMvc.perform(get("/api/account").header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.0));
        mockMvc.perform(get("/api/account").header("Authorization", bearer(recipient.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(25.0));
    }

    @Test
    void failedTransferLeavesBothAccountsAndLedgersUnchanged() throws Exception {
        Customer sender = registerCustomerAndLogin();
        Customer recipient = registerCustomerAndLogin();

        mockMvc.perform(post("/api/account/transfer")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(APPLICATION_JSON)
                        .content("{\"recipientAccountNumber\":\"" + recipient.accountNumber()
                                + "\",\"amount\":1.00}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/account").header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0.0));
        mockMvc.perform(get("/api/account").header("Authorization", bearer(recipient.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0.0));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from transactions t join accounts a on a.id = t.account_id "
                        + "where a.account_number in (?, ?) "
                        + "and t.transaction_type in ('TRANSFER_OUT', 'TRANSFER_IN')",
                Integer.class, sender.accountNumber(), recipient.accountNumber()));
    }

    @Test
    void transactionHistoryCanBeFilteredByTypeAndDateRange() throws Exception {
        String token = registerAndLogin();
        deposit(token, "10.00");
        mockMvc.perform(post("/api/account/withdraw")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":2.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/account/transactions?type=DEPOSIT")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"));

        mockMvc.perform(get("/api/account/transactions?from=2099-01-01T00:00:00Z")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/account/transactions?from=2026-10-11T00:00:00Z&to=2026-10-10T00:00:00Z")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        Customer customer = registerCustomerAndLogin();
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(customer.token())))
                .andExpect(status().isForbidden());

        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", customer.email());
        String adminToken = login(customer.email(), "StrongPass123!");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(customer.email()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountNumber").value(customer.accountNumber()));
    }

    @Test
    void loginIsRateLimitedAfterFiveFailedAttempts() throws Exception {
        String email = "locked-" + UUID.randomUUID() + "@example.com";
        String body = objectMapper.writeValueAsString(new LoginRequestBody(email, "WrongPass123!"));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> { request.setRemoteAddr("192.0.2.55"); return request; })
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.55"); return request; })
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        String token = registerAndLogin();
        deposit(token, "100.00");

        int requestCount = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Callable<Integer>> requests = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            requests.add(() -> {
                startGate.await();
                return mockMvc.perform(post("/api/account/withdraw")
                                .header("Authorization", bearer(token))
                                .contentType(APPLICATION_JSON)
                                .content("{\"amount\":10.00}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> results;
        try {
            results = requests.stream().map(executor::submit).toList();
            startGate.countDown();
            int successfulWithdrawals = 0;
            int rejectedWithdrawals = 0;

            for (Future<Integer> result : results) {
                int responseStatus = result.get();
                if (responseStatus == 200) {
                    successfulWithdrawals++;
                } else if (responseStatus == 422) {
                    rejectedWithdrawals++;
                }
            }

            assertEquals(10, successfulWithdrawals);
            assertEquals(10, rejectedWithdrawals);
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/account")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void ledgerInsertFailureRollsBackTheBalanceUpdate() throws Exception {
        String token = registerAndLogin();
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_test_ledger_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced ledger insert failure'
                        USING ERRCODE = '23514';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_test_ledger_insert
                BEFORE INSERT ON transactions
                FOR EACH ROW EXECUTE FUNCTION reject_test_ledger_insert()
                """);

        try {
            MvcResult result = mockMvc.perform(post("/api/account/deposit")
                            .header("Authorization", bearer(token))
                            .contentType(APPLICATION_JSON)
                            .content("{\"amount\":50.00}"))
                    .andReturn();

            assertNotEquals(200, result.getResponse().getStatus());
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_test_ledger_insert ON transactions");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_test_ledger_insert()");
        }

        mockMvc.perform(get("/api/account")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.0));

        mockMvc.perform(get("/api/account/transactions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private String registerAndLogin() throws Exception {
        return registerCustomerAndLogin().token();
    }

    private Customer registerCustomerAndLogin() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        String password = "StrongPass123!";
        String registration = objectMapper.writeValueAsString(new RegistrationRequest(
                "Test Customer", email, "+255712345678", password
        ));

        MvcResult registrationResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated())
                .andReturn();
        String accountNumber = objectMapper.readTree(
                registrationResult.getResponse().getContentAsString()).get("accountNumber").asText();

        String token = login(email, password);
        return new Customer(token, accountNumber, email);
    }

    private String login(String email, String password) throws Exception {
        String login = objectMapper.writeValueAsString(new LoginRequestBody(email, password));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }

    private void deposit(String token, String amount) throws Exception {
        mockMvc.perform(post("/api/account/deposit")
                        .header("Authorization", bearer(token))
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(
            String fullName,
            String email,
            String phoneNumber,
            String password
    ) {
    }

    private record LoginRequestBody(String email, String password) {
    }

    private record Customer(String token, String accountNumber, String email) {
    }
}
