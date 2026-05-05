package com.wallet.common.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    public static final String TOPIC = "user.registered";
    private UUID userId;
    private String email;
    private String fullName;
    private String phone;
    private LocalDateTime occurredAt;
}
