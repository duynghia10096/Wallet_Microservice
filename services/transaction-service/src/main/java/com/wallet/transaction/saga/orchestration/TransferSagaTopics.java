package com.wallet.transaction.saga.orchestration;

/**
 * ============================================================
 * CHOREOGRAPHY SAGA — TRANSFER FLOW
 * ============================================================
 *
 * Không có coordinator trung tâm. Mỗi service lắng nghe event
 * và tự biết phải làm gì tiếp theo (hoặc compensate nếu lỗi).
 *
 * HAPPY PATH:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ transaction-svc                                                  │
 * │   → publish TransferInitiatedEvent                               │
 * └──────────────────────────┬──────────────────────────────────────┘
 *                            ▼ Kafka: transfer.initiated
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ wallet-svc (Step 1)                                              │
 * │   → debit source wallet                                          │
 * │   → publish SourceWalletDebitedEvent  (success)                 │
 * │   → publish SourceWalletDebitFailedEvent (fail → trigger comp.) │
 * └──────────────────────────┬──────────────────────────────────────┘
 *                            ▼ Kafka: wallet.source.debited
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ wallet-svc (Step 2)                                              │
 * │   → credit destination wallet                                    │
 * │   → publish DestWalletCreditedEvent   (success)                 │
 * │   → publish DestWalletCreditFailedEvent (fail → trigger comp.)  │
 * └──────────────────────────┬──────────────────────────────────────┘
 *                            ▼ Kafka: wallet.dest.credited
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ transaction-svc                                                  │
 * │   → mark transaction COMPLETED                                   │
 * │   → publish TransactionCompletedEvent                            │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * COMPENSATION PATH (nếu credit dest thất bại):
 * DestWalletCreditFailedEvent
 *   → wallet-svc: reverse debit source (compensate step 1)
 *   → publish SourceWalletDebitReversedEvent
 *     → transaction-svc: mark FAILED
 */
public final class TransferSagaTopics {
    // Forward events
    public static final String TRANSFER_INITIATED       = "saga.transfer.initiated";
    public static final String SOURCE_WALLET_DEBITED    = "saga.wallet.source.debited";
    public static final String DEST_WALLET_CREDITED     = "saga.wallet.dest.credited";

    // Failure / compensation trigger events
    public static final String SOURCE_DEBIT_FAILED      = "saga.wallet.source.debit.failed";
    public static final String DEST_CREDIT_FAILED       = "saga.wallet.dest.credit.failed";

    // Compensation result events
    public static final String SOURCE_DEBIT_REVERSED    = "saga.wallet.source.debit.reversed";

    // Final outcome
    public static final String TRANSFER_COMPLETED       = "saga.transfer.completed";
    public static final String TRANSFER_FAILED          = "saga.transfer.failed";

    private TransferSagaTopics() {}
}
