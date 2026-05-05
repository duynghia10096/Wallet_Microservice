package com.wallet.wallet.infrastructure.messaging;

import com.wallet.common.event.UserRegisteredEvent;
import com.wallet.wallet.application.service.WalletApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Listens for domain events from other services.
 * When a new user registers, automatically create their default personal wallet.
 *
 * This is the EVENT-DRIVEN communication between microservices.
 * Wallet service does NOT call User service directly - it reacts to events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletEventConsumer {

    private final WalletApplicationService walletService;

    @KafkaListener(
            topics = UserRegisteredEvent.TOPIC,
            groupId = "wallet-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(
            @Payload UserRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment ack) {

        log.info("Received UserRegisteredEvent for user: {}", event.getUserId());

        try {
            // Auto-create default personal VND wallet for new user
            walletService.createDefaultWalletForUser(event.getUserId(), event.getEmail());
            ack.acknowledge();
            log.info("Default wallet created for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to create wallet for user: {}", event.getUserId(), e);
            // Don't ack - will be retried
            // In production: use dead letter queue after max retries
        }
    }
}
