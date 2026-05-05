package com.wallet.transaction.interfaces.rest.controller;

import com.wallet.common.dto.ApiResponse;
import com.wallet.transaction.application.dto.TransactionDto;
import com.wallet.transaction.application.dto.TransferRequest;
import com.wallet.transaction.application.service.TransactionApplicationService;
import com.wallet.transaction.saga.orchestration.command.SagaCommands;
import com.wallet.transaction.saga.orchestration.command.SagaCommands.CommandReply;
import com.wallet.transaction.saga.orchestration.coordinator.TransferSagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction endpoints for transfers and history")
@Validated
public class TransactionController {

    private final TransactionApplicationService transactionService;
    @Qualifier("orchestrationTransferSagaOrchestrator")
    private final TransferSagaOrchestrator transferSagaOrchestrator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/transfer")
    @Operation(summary = "Execute a wallet transfer")
    public ResponseEntity<ApiResponse<TransactionDto>> transfer(
            @Valid @RequestBody TransferRequest request) {
        TransactionDto result = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PostMapping("/transfer/saga-test")
    @Operation(summary = "Execute transfer via saga orchestrator (test endpoint)")
    public ResponseEntity<ApiResponse<TransactionDto>> transferSagaTest(
            @Valid @RequestBody TransferRequest request) {
        TransactionDto result = transferSagaOrchestrator.startSaga(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @PostMapping("/transfer/saga-test/replies/debit")
    @Operation(summary = "Simulate DEBIT reply to trigger onDebitReply in orchestrator")
    public ResponseEntity<ApiResponse<String>> simulateDebitReply(
            @Valid @RequestBody SagaReplyTestRequest request) {
        publishReply(SagaCommands.DEBIT_REPLY, request);
        return ResponseEntity.ok(ApiResponse.success("Debit reply published"));
    }

    @PostMapping("/transfer/saga-test/replies/credit")
    @Operation(summary = "Simulate CREDIT reply to trigger onCreditReply in orchestrator")
    public ResponseEntity<ApiResponse<String>> simulateCreditReply(
            @Valid @RequestBody SagaReplyTestRequest request) {
        publishReply(SagaCommands.CREDIT_REPLY, request);
        return ResponseEntity.ok(ApiResponse.success("Credit reply published"));
    }

    @PostMapping("/transfer/saga-test/replies/reverse")
    @Operation(summary = "Simulate REVERSE reply to trigger onReverseReply in orchestrator")
    public ResponseEntity<ApiResponse<String>> simulateReverseReply(
            @Valid @RequestBody SagaReplyTestRequest request) {
        publishReply(SagaCommands.REVERSE_REPLY, request);
        return ResponseEntity.ok(ApiResponse.success("Reverse reply published"));
    }

    @PostMapping("/transfer/saga-test/recover")
    @Operation(summary = "Manually trigger recoverStuckSagas for test")
    public ResponseEntity<ApiResponse<String>> triggerSagaRecovery() {
        transferSagaOrchestrator.recoverStuckSagas();
        return ResponseEntity.ok(ApiResponse.success("Saga recovery executed"));
    }

    @GetMapping("/wallet/{walletId}")
    @Operation(summary = "Get transaction history for a wallet")
    public ResponseEntity<ApiResponse<Page<TransactionDto>>> getWalletTransactions(
            @PathVariable("walletId") UUID walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TransactionDto> transactions = transactionService.getWalletTransactions(walletId, pageable);
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a specific transaction by ID")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransaction(@PathVariable UUID transactionId) {
        TransactionDto transaction = transactionService.getTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    private void publishReply(String topic, SagaReplyTestRequest request) {
        CommandReply reply = CommandReply.builder()
                .sagaId(request.getSagaId())
                .transactionId(request.getTransactionId())
                .commandType(request.getCommandType())
                .success(request.isSuccess())
                .failureReason(request.getFailureReason())
                .balanceAfter(request.getBalanceAfter())
                .repliedAt(LocalDateTime.now())
                .build();
        kafkaTemplate.send(topic, request.getSagaId().toString(), reply);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SagaReplyTestRequest {
        private UUID sagaId;
        private UUID transactionId;
        private String commandType;
        private boolean success;
        private String failureReason;
        private BigDecimal balanceAfter;
    }
}
