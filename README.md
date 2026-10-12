# Mini Banking System API

A Spring Boot REST API for customer registration, JWT login, account balances, deposits, withdrawals, and paginated transaction history.

## Requirements

- Docker with Docker Compose, or Java 17+, Maven, and PostgreSQL 16
- A strong database password and a Base64 encoded JWT key containing at least 32 bytes
- `CORS_ALLOWED_ORIGIN` should match the frontend origin; it defaults to `http://localhost:5173`

## Run with Docker Compose

1. Copy `.env.example` to `.env`.
2. Set `DB_PASSWORD` and `JWT_SECRET` in `.env`. Generate a key with `openssl rand -base64 32`.
3. Start the database and API:

   ```sh
   docker compose up --build
   ```

Flyway applies the database migration when the API starts. The API listens on `http://localhost:8080`.

The Compose PostgreSQL database is named `minibank`. Its container port is `5432`, published to host port `5433` so a database client such as pgAdmin can connect without conflicting with a local PostgreSQL server on port `5432`. In pgAdmin, register a server using host `localhost`, port `5433`, maintenance database `minibank`, username `minibank`, and the `DB_PASSWORD` value from `.env`.

## Run locally

Start PostgreSQL and create a database, then set these environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/Minibaking
DB_USERNAME=your_database_user
DB_PASSWORD=your_database_password
JWT_SECRET=your_base64_encoded_key
CORS_ALLOWED_ORIGIN=http://localhost:5173
```

Run the application with Maven:

```sh
mvn spring-boot:run
```

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Use the **Authorize** control in Swagger UI to supply `Bearer <accessToken>` for protected account endpoints.

## Endpoints

| Method | Path | Authentication | Description |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | No | Register a customer and create an account |
| POST | `/api/auth/login` | No | Exchange credentials for a one-hour JWT |
| GET | `/api/auth/me` | Bearer JWT | Return the signed-in user's role for dashboard routing |
| GET | `/api/account` | Bearer JWT | View the signed-in customer's account |
| POST | `/api/account/deposit` | Bearer JWT | Deposit a positive amount |
| POST | `/api/account/withdraw` | Bearer JWT | Withdraw an amount within the available balance |
| POST | `/api/account/transfer` | Bearer JWT | Transfer funds to another account number |
| GET | `/api/account/transactions?page=0&size=10&type=DEPOSIT&from=2026-10-01T00:00:00Z&to=2026-10-31T23:59:59Z` | Bearer JWT | View filtered, newest-first transaction history |
| GET | `/api/admin/users?page=0&size=20` | Admin JWT | List users without password data |
| GET | `/api/admin/accounts?page=0&size=20` | Admin JWT | List accounts and balances |
| POST | `/api/admin/admins` | Superadmin JWT | Create an `ADMIN` login |

Amounts must be positive and contain at most two decimal places. The account owner is derived from the JWT; account IDs are not accepted from clients.

Transfer request example (the recipient must be a different existing account):

```json
{
  "recipientAccountNumber": "2026000002",
  "amount": 100.00
}
```

A successful transfer atomically debits the sender, credits the recipient, and records `TRANSFER_OUT` and `TRANSFER_IN` entries in their respective histories.

Transaction history accepts optional `type` (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER_OUT`, or `TRANSFER_IN`), `from`, and `to` filters. Use ISO-8601 UTC timestamps for the date filters; both endpoints are inclusive. Page sizes are limited to 100.

Login throttling allows five failed attempts per client IP and normalized email in a rolling one-minute window. Further attempts return HTTP 429 with a `Retry-After: 60` header. This limiter is held in application memory and resets on restart; use a shared store if deploying multiple API instances.

Public registration always creates a `USER`, creates their account, and returns a JWT in the same response so the frontend can route them to the user dashboard. The token includes the role; `GET /api/auth/me` also returns it for clients that need to check the current session. Swagger shows the `USER` role and access token in the registration response.

Configure `SUPER_ADMIN_FULL_NAME`, `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PHONE_NUMBER`, and `SUPER_ADMIN_PASSWORD` in `.env` before starting Compose to create the initial `SUPER_ADMIN`. The bootstrap is idempotent; if the configured email belongs to another role, application startup fails. The superadmin can create additional administrators with `POST /api/admin/admins`. Admins can use the read-only `/api/admin/users` and `/api/admin/accounts` endpoints but cannot create admins. Public registration never accepts a role.

## Example requests

Register:

```json
{
  "fullName": "Ada Lovelace",
  "email": "ada@example.com",
  "phoneNumber": "+255712345678",
  "password": "StrongPass123!"
}
```

Login:

```json
{
  "email": "ada@example.com",
  "password": "StrongPass123!"
}
```

Deposit or withdraw:

```json
{
  "amount": 1000.00
}
```

## Tests

Run the tests and enforce the assessment's 70% line-coverage target for each service:

```sh
mvn verify
```

The coverage report is written to `target/site/jacoco/index.html`. Service tests use Mockito; API integration tests start a PostgreSQL 16 Testcontainer and therefore require Docker Desktop to be running.

## Postman

Import `postman/Mini Banking System API.postman_collection.json`. Set the collection variables as needed, then run Register and Login before calling the protected endpoints. The login request saves the returned token to the `accessToken` variable.
