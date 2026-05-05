-- ══════════════════════════════════════════════════════════════════
-- Orchestration SAGA: Coordinator state table
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE saga_instances (
    id                  UUID          PRIMARY KEY,
    transaction_id      UUID          NOT NULL UNIQUE,
    transaction_ref     VARCHAR(50)   NOT NULL,
    source_wallet_id    UUID          NOT NULL,
    dest_wallet_id      UUID          NOT NULL,
    user_id             UUID          NOT NULL,
    amount              NUMERIC(20,2) NOT NULL,
    fee_amount          NUMERIC(20,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    state               VARCHAR(30)   NOT NULL,
    failure_reason      VARCHAR(500),
    current_step        VARCHAR(50),
    retry_count         INTEGER       NOT NULL DEFAULT 0,
    last_step_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_saga_transaction_id ON saga_instances(transaction_id);
CREATE INDEX idx_saga_state          ON saga_instances(state);
CREATE INDEX idx_saga_user_id        ON saga_instances(user_id);

-- Partial index used exclusively by recovery job — fast because it only indexes non-terminal rows
CREATE INDEX idx_saga_stuck ON saga_instances(last_step_at, state)
    WHERE state NOT IN ('COMPLETED','COMPENSATED','FAILED','COMPENSATION_FAILED','TIMED_OUT');

CREATE OR REPLACE FUNCTION fn_update_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$;

CREATE TRIGGER trg_saga_updated_at
    BEFORE UPDATE ON saga_instances
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();
