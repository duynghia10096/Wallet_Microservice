package com.wallet.transaction.interfaces.rest.controller;

import com.wallet.common.dto.ApiResponse;
import com.wallet.transaction.saga.state.SagaInstance;
import com.wallet.transaction.saga.state.SagaInstanceRepository;
import com.wallet.transaction.saga.state.SagaState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal ops endpoint — should be behind ADMIN role or internal network only.
 *
 * Useful for:
 * ─ "Why is this transfer stuck?" GET /admin/sagas/{sagaId}
 * ─ "How many sagas need manual fix?" GET
 * /admin/sagas/state/COMPENSATION_FAILED
 * ─ "Show me metrics by state" GET /admin/sagas/metrics
 */
@RestController
@RequestMapping("/v1/admin/sagas")
@RequiredArgsConstructor
@Tag(name = "Saga Admin", description = "Ops endpoints for saga monitoring and recovery")
public class SagaAdminController {

    private final SagaInstanceRepository sagaRepository;

    @GetMapping("/{sagaId}")
    @Operation(summary = "Get saga instance by ID — for debugging a specific transfer")
    public ResponseEntity<ApiResponse<SagaInstanceDto>> getSaga(@PathVariable UUID sagaId) {
        return sagaRepository.findById(sagaId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(toDto(s))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get saga by transaction ID — find saga from user's transaction ref")
    public ResponseEntity<ApiResponse<SagaInstanceDto>> getByTransaction(@PathVariable UUID transactionId) {
        return sagaRepository.findByTransactionId(transactionId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(toDto(s))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/state/{state}")
    @Operation(summary = "List all sagas in a given state — e.g. COMPENSATION_FAILED for manual fix queue")
    public ResponseEntity<ApiResponse<List<SagaInstanceDto>>> getByState(@PathVariable SagaState state) {
        List<SagaInstanceDto> sagas = sagaRepository.findByState(state)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(sagas));
    }

    @GetMapping("/stuck")
    @Operation(summary = "List sagas stuck > N minutes (recovery candidates)")
    public ResponseEntity<ApiResponse<List<SagaInstanceDto>>> getStuck(
            @RequestParam(defaultValue = "10") int minutes) {
        List<SagaInstanceDto> sagas = sagaRepository
                .findStuckSagas(LocalDateTime.now().minusMinutes(minutes))
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(sagas));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Count sagas by state — for dashboards and alerts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getMetrics() {
        Map<String, Long> metrics = sagaRepository.countByState()
                .stream()
                .collect(Collectors.toMap(
                        row -> ((SagaState) row[0]).name(),
                        row -> (Long) row[1]));
        return ResponseEntity.ok(ApiResponse.success(metrics));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "All sagas for a user — audit trail")
    public ResponseEntity<ApiResponse<List<SagaInstanceDto>>> getUserSagas(@PathVariable UUID userId) {
        List<SagaInstanceDto> sagas = sagaRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(sagas));
    }

    private SagaInstanceDto toDto(SagaInstance s) {
        return SagaInstanceDto.builder()
                .sagaId(s.getId())
                .transactionId(s.getTransactionId())
                .transactionRef(s.getTransactionRef())
                .sourceWalletId(s.getSourceWalletId())
                .destWalletId(s.getDestWalletId())
                .userId(s.getUserId())
                .amount(s.getAmount())
                .feeAmount(s.getFeeAmount())
                .currency(s.getCurrency())
                .state(s.getState().name())
                .currentStep(s.getCurrentStep())
                .failureReason(s.getFailureReason())
                .retryCount(s.getRetryCount())
                .lastStepAt(s.getLastStepAt())
                .createdAt(s.getCreatedAt())
                .build();
    }

    @Value
    @Builder
    public static class SagaInstanceDto {
        UUID sagaId;
        UUID transactionId;
        String transactionRef;
        UUID sourceWalletId;
        UUID destWalletId;
        UUID userId;
        BigDecimal amount;
        BigDecimal feeAmount;
        String currency;
        String state;
        String currentStep;
        String failureReason;
        int retryCount;
        LocalDateTime lastStepAt;
        LocalDateTime createdAt;
    }
}
