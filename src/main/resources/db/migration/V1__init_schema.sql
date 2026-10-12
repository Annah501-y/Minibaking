CREATE TABLE users (
                       id            BIGSERIAL PRIMARY KEY,
                       full_name     VARCHAR(150) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       phone_number  VARCHAR(20) NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       role          VARCHAR(20) NOT NULL DEFAULT 'USER',
                       created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

                       CONSTRAINT uq_users_email UNIQUE (email),
                       CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE accounts (
                          id             BIGSERIAL PRIMARY KEY,
                          user_id        BIGINT NOT NULL,
                          account_number VARCHAR(10) NOT NULL,
                          balance        NUMERIC(19, 2) NOT NULL DEFAULT 0,
                          version        BIGINT NOT NULL DEFAULT 0,
                          created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                          updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

                          CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(id),
                          CONSTRAINT uq_accounts_user UNIQUE (user_id),
                          CONSTRAINT uq_accounts_number UNIQUE (account_number),
                          CONSTRAINT chk_accounts_balance CHECK (balance >= 0)
);

CREATE TABLE transactions (
                              id                        BIGSERIAL PRIMARY KEY,
                              account_id                BIGINT NOT NULL,
                              transaction_type          VARCHAR(20) NOT NULL,
                              amount                    NUMERIC(19, 2) NOT NULL,
                              balance_after_transaction NUMERIC(19, 2) NOT NULL,
                              reference                 VARCHAR(36) NOT NULL,
                              created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

                              CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id),
                              CONSTRAINT uq_tx_reference UNIQUE (reference),
                              CONSTRAINT chk_tx_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL')),
                              CONSTRAINT chk_tx_amount CHECK (amount > 0),
                              CONSTRAINT chk_tx_balance CHECK (balance_after_transaction >= 0)
);

CREATE INDEX idx_tx_account_created
    ON transactions (account_id, created_at DESC, id DESC);