package com.dtrps.modules.auth.repository;

import com.dtrps.modules.auth.entity.EmailOtp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
  /** The newest code that is still in play (not replaced, not used) */
  Optional<EmailOtp> findFirstByEmailAndPurposeAndConsumedAtIsNullOrderByOtpIdDesc(
      String email, String purpose);

  Optional<EmailOtp> findFirstByTicketHashAndPurpose(String ticketHash, String purpose);

  long countByEmailAndPurposeAndCreatedAtAfter(String email, String purpose, Instant since);

  /** A new code replaces the older ones of the same email and purpose */
  @Modifying
  @Query(
      "update EmailOtp o set o.consumedAt = :now"
          + " where o.email = :email and o.purpose = :purpose and o.consumedAt is null")
  int retire(
      @Param("email") String email, @Param("purpose") String purpose, @Param("now") Instant now);
}
