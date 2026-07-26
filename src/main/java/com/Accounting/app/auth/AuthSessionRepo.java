package com.Accounting.app.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AuthSessionRepo extends JpaRepository<AuthSession, UUID> {
    boolean existsByIdAndUser_EmailAndRevokedAtIsNullAndExpiresAtAfter(
            UUID id,
            String email,
            Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthSession session join fetch session.user "
            + "where session.refreshTokenHash = :refreshTokenHash "
            + "or session.previousRefreshTokenHash = :refreshTokenHash")
    Optional<AuthSession> findByRefreshTokenHashForUpdate(
            @Param("refreshTokenHash") String refreshTokenHash);
}
