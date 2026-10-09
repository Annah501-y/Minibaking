Mini Banking System API – SDLC & Database Design Documentation
Prepared by: Annie · Version 1.0 · October 2026 · Stack: Spring Boot, Spring Data JPA, PostgreSQL, JWT
1. Project Overview
   1.1 Purpose
   This document covers every phase of the Software Development Life Cycle (SDLC) for the Mini Banking System API, with a detailed database design. The system lets a user register, log in, hold one bank account, deposit, withdraw and view paginated transaction history. A React frontend will consume the API.
   1.2 Scope
   In scope: registration, JWT login, account view, deposit, withdrawal, transaction history, security, validation, error handling, tests, Docker, API docs.
   Optional (bonus): account-to-account transfer, admin endpoints, login rate limiting, history filtering, deployment.
   Out of scope: interest, loans, cards, multi-currency, KYC, email/SMS verification.
   1.3 Methodology
   The 2-day timeframe suits a lightweight iterative (Agile-style) approach: short cycles of design → build → test, with each user story delivered as a vertical slice (controller → service → repository → test).
   Phase
   Output
   Planned time
1. Planning
   Scope, stack, assumptions, risks
   1 hr
2. Requirements
   SRS (Section 2)
   2 hrs
3. Design
   Architecture, API, DB design (Sections 3–5)
   3 hrs
4. Implementation
   Source code, migrations
   6–8 hrs
5. Testing
   Unit + integration tests (Section 7)
   3 hrs
6. Deployment
   Docker, README, Postman/Swagger
   2 hrs
7. Maintenance
   Logging, monitoring, backlog (Section 9)
   Ongoing
2. Phase 1–2: Planning & Requirements (SRS)
   2.1 Stakeholders
   Stakeholder
   Interest
   Customer (end user)
   Safe account, accurate balance and history
   Frontend developer
   Stable, well-documented REST API
   Assessor / reviewer
   Code quality, security, DB design, business logic
   Admin (bonus)
   Visibility of users and accounts
   2.2 Functional Requirements
   ID
   Requirement
   Priority
   FR-01
   Register with full name, email, phone number, password
   Must
   FR-02
   Email must be unique
   Must
   FR-03
   Password hashed (BCrypt) before saving
   Must
   FR-04
   Account auto-created at registration with unique account number and balance 0
   Must
   FR-05
   Login with email and password; return JWT
   Must
   FR-06
   Protected endpoints require valid JWT
   Must
   FR-07
   View account holder name, account number, balance
   Must
   FR-08
   Deposit amount > 0 increases balance and records a transaction
   Must
   FR-09
   Withdraw amount > 0 and ≤ balance; decreases balance and records a transaction
   Must
   FR-10
   Each transaction stores type, amount, date/time, balance after
   Must
   FR-11
   Transaction history, newest first, paginated
   Must
   FR-12
   A user can only access their own account
   Must
   FR-13
   Transfer between accounts
   Bonus
   FR-14
   Admin lists all users and accounts
   Bonus
   FR-15
   Rate-limit failed logins
   Bonus
   FR-16
   Email/phone format validation
   Bonus
   2.3 Non-Functional Requirements
   ID
   Category
   Requirement
   NFR-01
   Security
   BCrypt hashing, stateless JWT, HTTPS in production, no sensitive data in logs
   NFR-02
   Data integrity
   Balance and transaction written in one atomic DB transaction
   NFR-03
   Concurrency
   Simultaneous withdrawals must never overdraw an account
   NFR-04
   Accuracy
   Money stored as exact decimals, never floating point
   NFR-05
   Performance
   Balance and history requests under 300 ms for typical load; indexed queries
   NFR-06
   Usability
   Consistent JSON responses and clear error messages for the frontend
   NFR-07
   Maintainability
   Layered architecture, SOLID, ≥ 70% coverage on service layer
   NFR-08
   Portability
   Runs via docker compose up with env-variable configuration
   2.4 Use Cases
   ID
   Use case
   Actor
   Main flow
   UC-01
   Register
   Visitor
   Submit details → validate → hash password → save user → create account → return summary
   UC-02
   Login
   Visitor
   Submit credentials → verify → issue JWT
   UC-03
   View account
   Customer
   Send JWT → system resolves user's account → return details
   UC-04
   Deposit
   Customer
   Submit amount → lock account → add → save transaction → return receipt
   UC-05
   Withdraw
   Customer
   Submit amount → lock account → check funds → subtract → save transaction
   UC-06
   View history
   Customer
   Request page → return newest-first transactions
   Alternative flows: duplicate email (409); invalid input (400); bad credentials (401); insufficient funds (422); missing/expired token (401).
   2.5 Business Rules
   ID
   Rule
   BR-01
   Users access only their own account (account is derived from the JWT, never from a client-supplied ID)
   BR-02
   Deposit and withdrawal amounts must be positive, max 2 decimal places
   BR-03
   Balance can never go below 0
   BR-04
   Every successful deposit/withdrawal creates exactly one transaction
   BR-05
   Transactions are immutable (no update or delete)
   BR-06
   Failed operations change nothing (no balance change, no transaction)
   BR-07
   Passwords are never stored or logged in plain text
   2.6 Assumptions
1. One user has exactly one account (the schema allows extending to many).
2. Single currency; amounts have 2 decimal places.
3. Account numbers are 10-digit numeric strings generated by the system.
4. Phone numbers use international format (e.g. +255712345678).
5. No email verification or password reset in this version.
6. JWT access tokens last 1 hour; no refresh tokens in this version.
   2.7 Risks
   Risk
   Impact
   Mitigation
   Race condition on simultaneous withdrawals
   Overdraft
   Pessimistic row lock + DB CHECK (balance >= 0)
   Weak JWT secret
   Token forgery
   Secret from env variable, ≥ 256 bits
   Floating-point errors
   Wrong balances
   BigDecimal / NUMERIC(19,2)
   Tight 2-day schedule
   Incomplete features
   Core first, bonus features only after
   Brute-force login
   Account takeover
   Generic error messages, optional rate limiter
3. Phase 3: System Design
   3.1 Architecture
   A layered monolith REST API:
   React client → Security filter chain (JWT) → Controllers → Services (@Transactional) → Repositories (Spring Data JPA) → PostgreSQL
   Layer
   Responsibility
   Controller
   HTTP mapping, request validation (@Valid), DTO conversion
   Service
   Business rules, transactions, locking
   Repository
   Data access via Spring Data JPA
   Entity
   JPA mapping of tables
   DTO
   Request/response contracts (entities never exposed)
   Security
   JWT filter, UserDetailsService, BCrypt encoder
   Exception
   @RestControllerAdvice mapping exceptions to HTTP errors
   3.2 Package Structure
   com.example.minibank
   ├── config          (SecurityConfig, OpenApiConfig)
   ├── security        (JwtService, JwtAuthFilter, CustomUserDetailsService)
   ├── controller      (AuthController, AccountController)
   ├── service         (AuthService, AccountService, TransactionService)
   ├── repository      (UserRepository, AccountRepository, TransactionRepository)
   ├── entity          (User, Account, Transaction, TransactionType)
   ├── dto             (request/, response/)
   ├── exception       (GlobalExceptionHandler, custom exceptions)
   └── util            (AccountNumberGenerator)
   3.3 Key Design Decisions
   Decision
   Reason
   BigDecimal + NUMERIC(19,2)
   Exact money arithmetic
   Pessimistic locking (SELECT … FOR UPDATE) on the account row
   Serialises concurrent deposits/withdrawals; simple and correct for banking
   @Transactional on deposit/withdraw
   Balance update and transaction insert commit or roll back together
   Stateless JWT (HS256)
   No server sessions; fits a React SPA
   Account looked up from the authenticated user
   Prevents access to other users' accounts (IDOR)
   Flyway migrations
   Versioned, reproducible schema
   balance_after stored on each transaction
   Gives an audit trail and fast history display
   Immutable ledger rows
   Auditability; no edits or deletes
   UUID or BIGINT keys
   BIGINT identity used here for simplicity and index speed; UUIDs are a valid alternative
   3.4 Security Design
   Concern
   Control
   Password storage
   BCrypt (strength 10–12)
   Authentication
   Authorization: Bearer <JWT>; claims: sub (user id), email, iat, exp
   Authorization
   Public: /api/auth/**, Swagger. Everything else authenticated. Admin routes require ROLE_ADMIN
   Input validation
   Bean Validation (@NotBlank, @Email, @DecimalMin, @Digits, @Pattern)
   Error disclosure
   Generic "Invalid email or password"; no stack traces to clients
   Secrets
   JWT_SECRET, DB_PASSWORD from environment variables
   CORS
   Allow only the React frontend origin
   Transport
   HTTPS in production
   Logging
   Never log passwords, tokens or full request bodies
   Brute force (bonus)
   Rate limit login per IP/email (e.g. Bucket4j: 5 attempts/minute)
4. API Specification
   Base path: /api. All bodies are JSON.
   Method
   Endpoint
   Auth
   Purpose
   Success
   POST
   /api/auth/register
   No
   Register + create account
   201
   POST
   /api/auth/login
   No
   Obtain JWT
   200
   GET
   /api/account
   Yes
   View account
   200
   POST
   /api/account/deposit
   Yes
   Deposit money
   200
   POST
   /api/account/withdraw
   Yes
   Withdraw money
   200
   GET
   /api/account/transactions?page=0&size=10
   Yes
   Paginated history
   200
   4.1 Register
   Request:
   {
   "fullName": "Annie Mushi",
   "email": "annie@example.com",
   "phoneNumber": "+255712345678",
   "password": "Str0ngPass!"
   }
   Response 201 Created:
   {
   "userId": 1,
   "fullName": "Annie Mushi",
   "email": "annie@example.com",
   "accountNumber": "2026000001",
   "balance": 0.00
   }
   4.2 Login
   Request: { "email": "annie@example.com", "password": "Str0ngPass!" }
   Response 200 OK:
   {
   "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
   "tokenType": "Bearer",
   "expiresIn": 3600
   }
   4.3 View Account
   Response 200 OK:
   {
   "accountHolderName": "Annie Mushi",
   "accountNumber": "2026000001",
   "balance": 150000.00
   }
   4.4 Deposit and Withdraw
   Request (both): { "amount": 50000.00 }
   Response 200 OK:
   {
   "transactionId": 12,
   "type": "DEPOSIT",
   "amount": 50000.00,
   "balanceAfter": 200000.00,
   "createdAt": "2026-10-09T10:15:30Z"
   }
   4.5 Transaction History
   Response 200 OK:
   {
   "content": [
   { "id": 12, "type": "DEPOSIT", "amount": 50000.00, "balanceAfter": 200000.00, "createdAt": "2026-10-09T10:15:30Z" },
   { "id": 11, "type": "WITHDRAWAL", "amount": 10000.00, "balanceAfter": 150000.00, "createdAt": "2026-10-08T08:02:11Z" }
   ],
   "page": 0,
   "size": 10,
   "totalElements": 2,
   "totalPages": 1
   }
   Sorted by created_at DESC, id DESC. Optional filters (bonus): type, from, to.
   4.6 Error Format and Status Codes
   {
   "timestamp": "2026-10-09T10:20:00Z",
   "status": 422,
   "error": "INSUFFICIENT_FUNDS",
   "message": "Insufficient balance for this withdrawal",
   "path": "/api/account/withdraw"
   }
   Status
   When
   400
   Validation failure (bad email, amount ≤ 0, too many decimals)
   401
   Missing/invalid/expired token; wrong credentials
   403
   Authenticated but not permitted (e.g. non-admin on admin route)
   404
   Resource not found
   409
   Email already registered
   422
   Insufficient funds
   429
   Too many login attempts (bonus)
   500
   Unexpected error (generic message, details logged server-side)
   4.7 Core Business Logic (withdrawal)
   @Transactional
   public TransactionResponse withdraw(Long userId, BigDecimal amount) {
   Account account = accountRepository.findByUserIdForUpdate(userId) // PESSIMISTIC_WRITE
   .orElseThrow(() -> new NotFoundException("Account not found"));
   if (account.getBalance().compareTo(amount) < 0)
   throw new InsufficientFundsException();
   account.setBalance(account.getBalance().subtract(amount));
   Transaction tx = transactionRepository.save(
   Transaction.of(account, TransactionType.WITHDRAWAL, amount, account.getBalance()));
   return TransactionResponse.from(tx);
   }
5. Database Design
   5.1 Conceptual Model
   • One User owns one Account (1 : 1; modelled as 1 : N-ready through a foreign key with a unique constraint).
   • One Account has many Transactions (1 : N).
   ┌──────────────┐ 1      1 ┌──────────────┐ 1      N ┌────────────────┐
   │    users     │──────────│   accounts   │──────────│  transactions  │
   ├──────────────┤          ├──────────────┤          ├────────────────┤
   │ PK id        │          │ PK id        │          │ PK id          │
   │ full_name    │          │ FK user_id   │          │ FK account_id  │
   │ email (UQ)   │          │ account_no UQ│          │ type           │
   │ phone_number │          │ balance      │          │ amount         │
   │ password_hash│          │ created_at   │          │ balance_after  │
   │ role         │          │ updated_at   │          │ created_at     │
   │ created_at   │          │ version      │          │ reference      │
   │ updated_at   │          └──────────────┘          └────────────────┘
   └──────────────┘
   5.2 Data Dictionary
   Table users
   Column
   Type
   Constraints
   Description
   id
   BIGSERIAL
   PK
   User identifier
   full_name
   VARCHAR(150)
   NOT NULL
   Full name
   email
   VARCHAR(255)
   NOT NULL, UNIQUE
   Login identifier (stored lowercase)
   phone_number
   VARCHAR(20)
   NOT NULL
   International format
   password_hash
   VARCHAR(100)
   NOT NULL
   BCrypt hash
   role
   VARCHAR(20)
   NOT NULL, DEFAULT 'USER'
   USER or ADMIN
   created_at
   TIMESTAMPTZ
   NOT NULL, DEFAULT now()
   Created
   updated_at
   TIMESTAMPTZ
   NOT NULL, DEFAULT now()
   Last updated
   Table accounts
   Column
   Type
   Constraints
   Description
   id
   BIGSERIAL
   PK
   Account identifier
   user_id
   BIGINT
   NOT NULL, UNIQUE, FK → users(id)
   Owner (unique = one account per user)
   account_number
   VARCHAR(10)
   NOT NULL, UNIQUE
   Public account number
   balance
   NUMERIC(19,2)
   NOT NULL, DEFAULT 0, CHECK ≥ 0
   Current balance
   version
   BIGINT
   NOT NULL, DEFAULT 0
   Optimistic-lock safety net
   created_at
   TIMESTAMPTZ
   NOT NULL, DEFAULT now()
   Created
   updated_at
   TIMESTAMPTZ
   NOT NULL, DEFAULT now()
   Last updated
   Table transactions
   Column
   Type
   Constraints
   Description
   id
   BIGSERIAL
   PK
   Transaction identifier
   account_id
   BIGINT
   NOT NULL, FK → accounts(id)
   Owning account
   transaction_type
   VARCHAR(20)
   NOT NULL, CHECK in (DEPOSIT,WITHDRAWAL)
   Type
   amount
   NUMERIC(19,2)
   NOT NULL, CHECK > 0
   Amount moved
   balance_after_transaction
   NUMERIC(19,2)
   NOT NULL, CHECK ≥ 0
   Balance after this transaction
   reference
   VARCHAR(36)
   NOT NULL, UNIQUE
   UUID reference for tracing
   created_at
   TIMESTAMPTZ
   NOT NULL, DEFAULT now()
   When it happened
   For the transfer bonus, extend the CHECK with TRANSFER_IN / TRANSFER_OUT and add a nullable related_account_id FK (new Flyway migration; no existing data changes).
   5.3 Schema (Flyway V1__init_schema.sql)
   CREATE TABLE users (
   id            BIGSERIAL PRIMARY KEY,
   full_name     VARCHAR(150) NOT NULL,
   email         VARCHAR(255) NOT NULL,
   phone_number  VARCHAR(20)  NOT NULL,
   password_hash VARCHAR(100) NOT NULL,
   role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
   created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
   updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
   CONSTRAINT uq_users_email UNIQUE (email),
   CONSTRAINT chk_users_role CHECK (role IN ('USER','ADMIN'))
   );

CREATE TABLE accounts (
id             BIGSERIAL PRIMARY KEY,
user_id        BIGINT       NOT NULL,
account_number VARCHAR(10)  NOT NULL,
balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
version        BIGINT       NOT NULL DEFAULT 0,
created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id),
CONSTRAINT uq_accounts_user UNIQUE (user_id),
CONSTRAINT uq_accounts_number UNIQUE (account_number),
CONSTRAINT chk_accounts_balance CHECK (balance >= 0)
);

CREATE TABLE transactions (
id                        BIGSERIAL PRIMARY KEY,
account_id                BIGINT        NOT NULL,
transaction_type          VARCHAR(20)   NOT NULL,
amount                    NUMERIC(19,2) NOT NULL,
balance_after_transaction NUMERIC(19,2) NOT NULL,
reference                 VARCHAR(36)   NOT NULL,
created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id),
CONSTRAINT uq_tx_reference UNIQUE (reference),
CONSTRAINT chk_tx_type CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL')),
CONSTRAINT chk_tx_amount CHECK (amount > 0),
CONSTRAINT chk_tx_balance CHECK (balance_after_transaction >= 0)
);

CREATE INDEX idx_tx_account_created ON transactions (account_id, created_at DESC, id DESC);
5.4 Indexes
Index
Purpose
uq_users_email
Login lookup and uniqueness
uq_accounts_user
Find account by user; enforces one account per user
uq_accounts_number
Unique account numbers; transfer lookup
idx_tx_account_created
Fast newest-first paginated history per account
uq_tx_reference
Unique trace reference
5.5 Integrity, Concurrency and Consistency
Scenario
Protection
Two withdrawals at once
SELECT … FOR UPDATE serialises them; second sees the reduced balance
Overdraft bug in code
CHECK (balance >= 0) rejects the update at DB level
Crash between balance update and ledger insert
Single @Transactional boundary → full rollback
Duplicate email on simultaneous registration
UNIQUE constraint; service maps violation to 409
Orphan records
Foreign keys; no cascading deletes on financial data
Tampering with history
No update/delete endpoints; app DB user can be limited to INSERT/SELECT on transactions
Reconciliation
accounts.balance must equal the latest balance_after_transaction for that account
5.6 Registration Flow (data)
Registration runs in one transaction: insert users (hashed password) → generate unique 10-digit account_number (retry on collision) → insert accounts with balance 0 → commit.
5.7 Sample Reconciliation Query
SELECT a.account_number, a.balance AS account_balance, t.balance_after_transaction AS ledger_balance
FROM accounts a
LEFT JOIN LATERAL (
SELECT balance_after_transaction FROM transactions
WHERE account_id = a.id ORDER BY created_at DESC, id DESC LIMIT 1
) t ON true
WHERE a.balance <> COALESCE(t.balance_after_transaction, 0);
An empty result means all balances are consistent.
5.8 Backup and Retention
Daily PostgreSQL backups (pg_dump), point-in-time recovery where available, and indefinite retention of transaction rows (financial audit records).
6. Phase 4: Implementation Plan
   6.1 Tech and Configuration
   Item
   Choice
   Language / Framework
   Java 17+, Spring Boot 3.x
   Dependencies
   Web, Data JPA, Security, Validation, PostgreSQL driver, Flyway, jjwt, springdoc-openapi, Lombok
   Build
   Maven
   Tests
   JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)
   Environment variables:
   Variable
   Example
   Purpose
   DB_URL
   jdbc:postgresql://localhost:5432/minibank
   Database URL
   DB_USERNAME / DB_PASSWORD
   minibank / (secret)
   DB credentials
   JWT_SECRET
   (≥ 32 chars, secret)
   Token signing key
   JWT_EXPIRATION_MS
   3600000
   Token lifetime
   CORS_ALLOWED_ORIGIN
   http://localhost:5173
   React origin
   6.2 Build Order (Iterations)
   Iteration
   Deliverable
   1
   Project setup, Flyway schema, entities, repositories
   2
   Registration + auto account creation
   3
   Login, JWT filter, security config
   4
   Get account, deposit, withdraw (with locking)
   5
   Transaction history with pagination
   6
   Global exception handling, validation polish
   7
   Tests, Swagger, Dockerfile, README, Postman collection
   8
   Bonus: transfers, admin, rate limiting, filtering
   6.3 Coding Standards
   Constructor injection; DTOs at API boundary; no business logic in controllers; meaningful names; Javadoc on public services; consistent error responses; no secrets in source control.
7. Phase 5: Test Plan
   7.1 Strategy
   Level
   Tools
   Focus
   Unit
   JUnit 5, Mockito
   Service rules (amounts, balance checks, hashing)
   Integration
   @SpringBootTest, MockMvc, Testcontainers
   Endpoints, security, DB constraints
   Concurrency
   ExecutorService
   Parallel withdrawals never overdraw
   Manual/API
   Postman / Swagger UI
   End-to-end acceptance
   7.2 Test Cases
   ID
   Scenario
   Expected
   TC-01
   Register with valid data
   201; user + account created; balance 0; password stored as hash
   TC-02
   Register with existing email
   409
   TC-03
   Register with invalid email/short password/missing field
   400 with field errors
   TC-04
   Login with valid credentials
   200 with JWT
   TC-05
   Login with wrong password
   401 generic message
   TC-06
   Get account without token
   401
   TC-07
   Get account with valid token
   200 with name, number, balance
   TC-08
   Deposit 1000
   Balance +1000; DEPOSIT transaction saved with correct balance_after
   TC-09
   Deposit 0 or negative
   400; no change
   TC-10
   Withdraw within balance
   Balance reduced; WITHDRAWAL transaction saved
   TC-11
   Withdraw more than balance
   422; balance and ledger unchanged
   TC-12
   Withdraw 0 or negative
   400
   TC-13
   History with several transactions
   Newest first; correct page metadata
   TC-14
   History pagination (page, size)
   Correct slices
   TC-15
   User A cannot see User B's data
   Responses always based on token owner
   TC-16
   20 parallel withdrawals on a limited balance
   Final balance ≥ 0; successes × amount = money withdrawn
   TC-17
   Expired / tampered JWT
   401
   TC-18
   Forced rollback after balance update
   No partial data
   TC-19
   Amount with 3 decimals
   400
   TC-20
   Login rate limit exceeded (bonus)
   429
   7.3 Exit Criteria
   All Must-priority requirements covered by passing tests; no open critical/high defects; core flows demonstrated in Postman.
   7.4 Traceability
   Requirement
   Test cases
   FR-01 – FR-04
   TC-01, 02, 03
   FR-05, FR-06
   TC-04, 05, 06, 17
   FR-07
   TC-07
   FR-08
   TC-08, 09
   FR-09
   TC-10, 11, 12, 16
   FR-10
   TC-08, 10
   FR-11
   TC-13, 14
   FR-12
   TC-15
   NFR-02, 03
   TC-16, 18
8. Phase 6: Deployment
   8.1 Docker
   Dockerfile (multi-stage: Maven build → slim JRE image) and docker-compose.yml with two services: db (postgres:16, persistent volume) and api (depends on db, reads env variables).
   services:
   db:
   image: postgres:16
   environment:
   POSTGRES_DB: minibank
   POSTGRES_USER: minibank
   POSTGRES_PASSWORD: ${DB_PASSWORD}
   volumes: [pgdata:/var/lib/postgresql/data]
   api:
   build: .
   ports: ["8080:8080"]
   environment:
   DB_URL: jdbc:postgresql://db:5432/minibank
   DB_USERNAME: minibank
   DB_PASSWORD: ${DB_PASSWORD}
   JWT_SECRET: ${JWT_SECRET}
   depends_on: [db]
   volumes:
   pgdata:
   8.2 Running Locally
1. Install Java 17+, Maven, Docker.
2. Copy .env.example to .env and set DB_PASSWORD and JWT_SECRET.
3. Run docker compose up --build.
4. API: http://localhost:8080 · Swagger UI: http://localhost:8080/swagger-ui.html.
5. Run tests: mvn test.
   8.3 Environments and Release
   Environment
   Notes
   Local
   Docker Compose
   Staging/free hosting (bonus)
   Render, Railway or Fly.io with managed PostgreSQL
   Release checklist: tests green, migrations applied automatically by Flyway, secrets set, CORS origin set, Swagger reviewed, README updated, Git tag v1.0.0.
9. Phase 7: Maintenance and Future Work
   • Monitoring: Spring Boot Actuator health endpoint, structured logging (SLF4J/Logback) with transaction references.
   • Support: log levels per environment; correlation ID per request.
   • Change control: every schema change is a new Flyway migration; API changes versioned (/api/v2).
   • Backlog: transfers, admin dashboard, refresh tokens, password reset, email/SMS verification, statements (PDF), idempotency keys for deposits/withdrawals, multiple accounts per user, audit log, account freezing.
10. Deliverables Checklist
    Deliverable (from assessment)
    Where covered
    Source code (Git/ZIP)
    Section 6
    README (install, run, env vars, endpoints, examples)
    Sections 4, 6.1, 8.2
    Database schema / migrations
    Section 5.3
    Postman collection / API docs
    Section 4 and Swagger
    Tests (register, login, deposit, withdraw, insufficient funds, history)
    Section 7
    Design decisions
    Section 3.3
    Assumptions
    Section 2.6