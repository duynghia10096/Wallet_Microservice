-- ══════════════════════════════════════════════════════════════════
-- Saga participant idempotency log
-- Records each saga operation applied to wallets.
-- Prevents double-apply when coordinator retries a command.
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE saga_operation_logs (
    id              UUID          PRIMARY KEY,
    transaction_id  UUID          NOT NULL,
    saga_id         UUID          NOT NULL,
    operation_type  VARCHAR(20)   NOT NULL,  -- DEBIT | CREDIT | REVERSED
    wallet_id       UUID          NOT NULL,
    amount          NUMERIC(20,2) NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Core idempotency constraint:
    -- One operation type per transaction → no double-debit/credit/reverse
    CONSTRAINT uq_saga_op_tx_type UNIQUE (transaction_id, operation_type)
);

CREATE INDEX idx_saga_op_transaction ON saga_operation_logs(transaction_id);
CREATE INDEX idx_saga_op_saga        ON saga_operation_logs(saga_id);
