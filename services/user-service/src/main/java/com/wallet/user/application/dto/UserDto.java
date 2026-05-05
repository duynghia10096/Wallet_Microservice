package com.wallet.user.application.dto;
import lombok.Builder; import lombok.Value;
import java.time.LocalDateTime; import java.util.UUID;
@Value @Builder
public class UserDto {
    UUID id; String username; String email; String phone;
    String fullName; String status; String kycStatus; int kycLevel;
    LocalDateTime createdAt;
}
