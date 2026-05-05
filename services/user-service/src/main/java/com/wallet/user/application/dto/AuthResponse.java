package com.wallet.user.application.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthResponse {
    String accessToken;
    String tokenType;
    Long expiresIn;
    UserDto user;
}
