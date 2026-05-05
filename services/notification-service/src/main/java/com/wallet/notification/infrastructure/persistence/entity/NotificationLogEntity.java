package com.wallet.notification.infrastructure.persistence.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name = "notification_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLogEntity {
    @Id @GeneratedValue private UUID id;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "notification_type", length = 50) private String notificationType;
    @Column(name = "message", length = 1000) private String message;
    @Column(name = "status", length = 20) private String status;
    @CreationTimestamp @Column(name = "created_at") private LocalDateTime createdAt;
}
