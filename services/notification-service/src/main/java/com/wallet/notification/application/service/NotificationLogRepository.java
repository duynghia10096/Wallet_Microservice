package com.wallet.notification.application.service;

import com.wallet.notification.infrastructure.persistence.entity.NotificationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface NotificationLogRepository
        extends org.springframework.data.repository.Repository<NotificationLogEntity, UUID> {
    NotificationLogEntity save(NotificationLogEntity entity);
}
