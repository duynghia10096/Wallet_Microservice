package com.wallet.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;

    public void sendWelcomeEmail(String email, String fullName) {
        String subject = "Welcome to Wallet Platform!";
        String body = String.format("""
                Hi %s,

                Welcome to Wallet Platform! Your account has been created successfully.
                Your personal wallet has been set up and is ready to use.

                Start by depositing funds to your wallet.

                Best regards,
                Wallet Platform Team
                """, fullName);

        sendEmail(email, subject, body);
        log.info("Welcome email sent to: {}", email);
    }

    public void sendTransactionSuccessNotification(UUID userId, String transactionRef,
            BigDecimal amount, String currency, String type) {
        // In production: look up user email from user-service or a local projection
        String formattedAmount = NumberFormat.getNumberInstance(new Locale("vi", "VN"))
                .format(amount) + " " + currency;

        log.info("Transaction success notification sent - userId: {} ref: {} amount: {}",
                userId, transactionRef, formattedAmount);

        // Save notification log for audit
        saveNotificationLog(userId, "TRANSACTION_SUCCESS",
                String.format("Transaction %s completed: %s", transactionRef, formattedAmount));
    }

    public void sendTransactionFailedNotification(UUID userId, String transactionRef,
            String reason) {
        log.warn("Transaction failure notification sent - userId: {} ref: {} reason: {}",
                userId, transactionRef, reason);

        saveNotificationLog(userId, "TRANSACTION_FAILED",
                String.format("Transaction %s failed: %s", transactionRef, reason));
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@walletplatform.com");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private void saveNotificationLog(UUID userId, String type, String message) {
        try {
            notificationLogRepository.save(
                    com.wallet.notification.infrastructure.persistence.entity.NotificationLogEntity.builder()
                            .userId(userId)
                            .notificationType(type)
                            .message(message)
                            .status("SENT")
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to save notification log: {}", e.getMessage());
        }
    }
}
