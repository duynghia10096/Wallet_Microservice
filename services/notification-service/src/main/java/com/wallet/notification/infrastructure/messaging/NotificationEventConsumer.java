package com.wallet.notification.infrastructure.messaging;

import com.wallet.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.wallet.notification.application.service.NotificationService;

/**
 * Notification Event Consumer.
 *
 * Listens to domain events from ALL services and sends appropriate notifications.
 * This service has NO REST API - it is purely event-driven (consumer only).
 *
 * Communication: Kafka (async, one-way)
 * - user-service → user.registered
 * - transaction-service → transaction.completed, transaction.failed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = UserRegisteredEvent.TOPIC, groupId = "notification-service-group")
    public void onUserRegistered(@Payload UserRegisteredEvent event, Acknowledgment ack) {
        log.info("Sending welcome notification to: {}", event.getEmail());
        try {
            notificationService.sendWelcomeEmail(event.getEmail(), event.getFullName());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = TransactionCompletedEvent.TOPIC, groupId = "notification-service-group")
    public void onTransactionCompleted(@Payload TransactionCompletedEvent event, Acknowledgment ack) {
        log.info("Transaction completed: {} amount: {} {}", event.getTransactionRef(),
                event.getAmount(), event.getCurrency());
        try {
            notificationService.sendTransactionSuccessNotification(
                    event.getUserId(),
                    event.getTransactionRef(),
                    event.getAmount(),
                    event.getCurrency(),
                    event.getTransactionType()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send transaction notification: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = TransactionFailedEvent.TOPIC, groupId = "notification-service-group")
    public void onTransactionFailed(@Payload TransactionFailedEvent event, Acknowledgment ack) {
        log.info("Transaction failed: {} reason: {}", event.getTransactionRef(), event.getFailureReason());
        try {
            notificationService.sendTransactionFailedNotification(
                    event.getUserId(),
                    event.getTransactionRef(),
                    event.getFailureReason()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to send failure notification: {}", e.getMessage());
        }
    }
}
