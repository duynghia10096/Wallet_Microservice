package com.wallet.user.domain.model;

import com.wallet.user.domain.exception.UserAlreadyExistsException;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Aggregate Root.
 */
@Getter
public class User {

    private UUID id;
    private String username;
    private String email;
    private String phone;
    private String passwordHash;
    private String fullName;
    private UserStatus status;
    private KycStatus kycStatus;
    private int kycLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long version;

    private User() {}

    public static User register(String username, String email, String phone,
                                String passwordHash, String fullName) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.username = username;
        user.email = email;
        user.phone = phone;
        user.passwordHash = passwordHash;
        user.fullName = fullName;
        user.status = UserStatus.ACTIVE;
        user.kycStatus = KycStatus.PENDING;
        user.kycLevel = 0;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        user.version = 0;
        return user;
    }

    public static User reconstitute(UUID id, String username, String email, String phone,
                                    String passwordHash, String fullName, UserStatus status,
                                    KycStatus kycStatus, int kycLevel,
                                    LocalDateTime createdAt, LocalDateTime updatedAt, long version) {
        User user = new User();
        user.id = id; user.username = username; user.email = email;
        user.phone = phone; user.passwordHash = passwordHash; user.fullName = fullName;
        user.status = status; user.kycStatus = kycStatus; user.kycLevel = kycLevel;
        user.createdAt = createdAt; user.updatedAt = updatedAt; user.version = version;
        return user;
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateKycStatus(KycStatus kycStatus, int kycLevel) {
        this.kycStatus = kycStatus;
        this.kycLevel = kycLevel;
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend(String reason) {
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
