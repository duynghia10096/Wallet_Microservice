-- ══════════════════════════════════════════════════════════════════
-- Transaction table for transaction-service domain model
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE transactions (
    id               UUID           PRIMARY KEY,
    transaction_ref  VARCHAR(255)   NOT NULL,
    idempotency_key  VARCHAR(255),
    source_wallet_id UUID           NOT NULL,
    dest_wallet_id   UUID           NOT NULL,
    user_id          UUID           NOT NULL,
    amount           NUMERIC(20,2)  NOT NULL,
    fee_amount       NUMERIC(20,2)  NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    type             VARCHAR(30)    NOT NULL,
    status           VARCHAR(30)    NOT NULL,
    description      VARCHAR(500),
    failure_reason   VARCHAR(500),
    initiated_at     TIMESTAMPTZ    NOT NULL,
    completed_at     TIMESTAMPTZ,
    version          BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_transactions_transaction_ref ON transactions(transaction_ref);
CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX idx_transactions_source_wallet_id ON transactions(source_wallet_id);
CREATE INDEX idx_transactions_dest_wallet_id ON transactions(dest_wallet_id);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_status ON transactions(status);
