package com.wallet.user.application.service;

import com.wallet.common.event.UserRegisteredEvent;
import com.wallet.user.application.dto.*;
import com.wallet.user.domain.exception.*;
import com.wallet.user.domain.model.User;
import com.wallet.user.domain.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("email: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("username: " + request.getUsername());
        }

        User user = User.register(
                request.getUsername(),
                request.getEmail(),
                request.getPhone(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName()
        );

        User saved = userRepository.save(user);

        // Publish event - other services (wallet-service) will auto-create a wallet
        kafkaTemplate.send(UserRegisteredEvent.TOPIC, saved.getId().toString(),
                UserRegisteredEvent.builder()
                        .userId(saved.getId())
                        .email(saved.getEmail())
                        .fullName(saved.getFullName())
                        .phone(saved.getPhone())
                        .occurredAt(LocalDateTime.now())
                        .build());

        log.info("User registered: {}", saved.getEmail());
        return buildAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActive()) {
            throw new com.wallet.common.exception.ServiceException(
                    "Account is not active", "ACCOUNT_INACTIVE",
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
        return toDto(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = generateToken(user);
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .user(toDto(user))
                .build();
    }

    private String generateToken(User user) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .claim("roles", List.of("USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus().name())
                .kycStatus(user.getKycStatus().name())
                .kycLevel(user.getKycLevel())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
