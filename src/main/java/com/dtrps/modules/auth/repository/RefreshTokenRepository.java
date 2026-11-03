package com.dtrps.modules.auth.repository;

import com.dtrps.modules.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(UUID familyId, Instant now);

  @Modifying
  @Query(
      "update RefreshToken t set t.revokedAt = :now"
          + " where t.user.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(Long userId, Instant now);

  @Modifying
  @Query("delete from RefreshToken t where t.user.userId = :userId and t.expiresAt < :now")
  int deleteExpiredForUser(Long userId, Instant now);
}
