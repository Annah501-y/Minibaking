CREATE INDEX idx_tx_account_type_created
    ON transactions (account_id, transaction_type, created_at DESC, id DESC);
