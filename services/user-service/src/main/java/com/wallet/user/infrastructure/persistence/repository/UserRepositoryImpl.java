package com.wallet.user.infrastructure.persistence.repository;

import com.wallet.user.domain.model.User;
import com.wallet.user.domain.repository.UserRepository;
import com.wallet.user.infrastructure.persistence.entity.UserJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    private UserJpaEntity toEntity(User u) {
        return UserJpaEntity.builder()
                .id(u.getId()).username(u.getUsername()).email(u.getEmail())
                .phone(u.getPhone()).passwordHash(u.getPasswordHash()).fullName(u.getFullName())
                .status(u.getStatus()).kycStatus(u.getKycStatus()).kycLevel(u.getKycLevel())
                .createdAt(u.getCreatedAt()).updatedAt(u.getUpdatedAt()).version(u.getVersion())
                .build();
    }

    private User toDomain(UserJpaEntity e) {
        return User.reconstitute(e.getId(), e.getUsername(), e.getEmail(), e.getPhone(),
                e.getPasswordHash(), e.getFullName(), e.getStatus(), e.getKycStatus(),
                e.getKycLevel(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }
}
