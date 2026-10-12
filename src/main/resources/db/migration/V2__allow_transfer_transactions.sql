ALTER TABLE transactions
    DROP CONSTRAINT chk_tx_type;

ALTER TABLE transactions
    ADD CONSTRAINT chk_tx_type
        CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_OUT', 'TRANSFER_IN'));
