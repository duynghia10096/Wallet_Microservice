package com.wallet.transaction.saga.step;

import com.wallet.transaction.saga.state.SagaInstance;

/**
 * Strategy interface for each saga step.
 *
 * Benefits of extracting steps:
 * ─ Each step is independently testable
 * ─ Coordinator stays thin (orchestrate only, no business logic)
 * ─ Easy to add new steps (e.g. FraudCheckStep, NotifyStep)
 */
public interface SagaStep {

    /**
     * Name shown in SagaInstance.currentStep for debugging.
     */
    String stepName();

    /**
     * Execute the step: send a command to the relevant worker.
     * Does NOT wait for reply — reply comes asynchronously.
     */
    void execute(SagaInstance saga);

    /**
     * Whether this step has a compensation (rollback) action.
     */
    default boolean hasCompensation() {
        return false;
    }

    /**
     * Compensate: undo what execute() did.
     * Called by coordinator when a later step fails.
     */
    default void compensate(SagaInstance saga) {
        throw new UnsupportedOperationException(
            "Step " + stepName() + " has no compensation");
    }
}
