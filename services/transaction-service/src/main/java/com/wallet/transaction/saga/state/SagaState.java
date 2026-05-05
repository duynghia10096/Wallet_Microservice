package com.wallet.transaction.saga.state;

/**
 * Orchestration SAGA — Transfer Money State Machine
 * ══════════════════════════════════════════════════
 *
 * HAPPY PATH:
 *
 *  CREATED
 *    │  (coordinator saves SagaInstance, sends DebitSourceCommand)
 *    ▼
 *  DEBIT_SOURCE_PENDING
 *    │  (wallet-service debits source, replies OK)
 *    ▼
 *  CREDIT_DEST_PENDING
 *    │  (wallet-service credits dest, replies OK)
 *    ▼
 *  COMPLETED ✅
 *
 * ─────────────────────────────────────────────────────────
 * COMPENSATION PATH (something failed after debit succeeded):
 *
 *  CREDIT_DEST_PENDING
 *    │  (wallet-service fails to credit dest)
 *    ▼
 *  REVERSING_SOURCE_DEBIT          ← compensation step
 *    │  (wallet-service refunds source, replies OK)
 *    ▼
 *  COMPENSATED ❌ (clean — money returned)
 *
 * ─────────────────────────────────────────────────────────
 * FAILURE PATH (debit failed → no compensation needed):
 *
 *  DEBIT_SOURCE_PENDING
 *    │  (wallet-service can't debit — insufficient balance, frozen, etc.)
 *    ▼
 *  FAILED ❌
 *
 * ─────────────────────────────────────────────────────────
 * CRITICAL PATHS:
 *
 *  REVERSING_SOURCE_DEBIT
 *    │  (compensation itself fails after N retries)
 *    ▼
 *  COMPENSATION_FAILED 🚨  → ops team manual fix
 *
 *  Any state (> timeout threshold with retries exhausted)
 *    ▼
 *  TIMED_OUT 🚨
 */
public enum SagaState {

    // ── Initial ────────────────────────────────────────────────────
    CREATED,

    // ── Forward steps ──────────────────────────────────────────────
    DEBIT_SOURCE_PENDING,
    CREDIT_DEST_PENDING,

    // ── Terminal: success ──────────────────────────────────────────
    COMPLETED,

    // ── Compensation steps ─────────────────────────────────────────
    REVERSING_SOURCE_DEBIT,

    // ── Terminal: compensated (money returned) ─────────────────────
    COMPENSATED,

    // ── Terminal: failed before any money moved ────────────────────
    FAILED,

    // ── Terminal: compensation itself failed — manual intervention ─
    COMPENSATION_FAILED,

    // ── Terminal: timed out after max retries ──────────────────────
    TIMED_OUT;

    public boolean isTerminal() {
        return switch (this) {
            case COMPLETED, COMPENSATED, FAILED, COMPENSATION_FAILED, TIMED_OUT -> true;
            default -> false;
        };
    }

    public boolean requiresCompensation() {
        // These states mean debit already happened → must reverse on failure
        return this == CREDIT_DEST_PENDING || this == REVERSING_SOURCE_DEBIT;
    }
}
