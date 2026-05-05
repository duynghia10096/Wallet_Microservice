package com.wallet.common.security;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class UserContext {
    UUID userId;
    String email;
    List<String> roles;

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
