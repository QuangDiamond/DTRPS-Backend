package com.dtrps.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A 6-digit code emailed to prove the address belongs to the person (sign-up, password reset). Only
 * hashes are stored. A correct code yields a one-time ticket that finishes the flow.
 */
@Entity
@Table(
    name = "email_otps",
    indexes = {
      @Index(name = "idx_email_otps_email_purpose", columnList = "email, purpose"),
      @Index(name = "idx_email_otps_ticket", columnList = "ticket_hash")
    })
@Check(name = "chk_email_otp_purpose", constraints = "purpose IN ('REGISTER', 'RESET_PASSWORD')")
@Getter
@Setter
public class EmailOtp {
  public static final String REGISTER = "REGISTER";
  public static final String RESET_PASSWORD = "RESET_PASSWORD";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "otp_id")
  private Long otpId;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "purpose", nullable = false, length = 30)
  private String purpose;

  /** SHA-256 of the code, salted with email and purpose */
  @Column(name = "code_hash", nullable = false, length = 64)
  private String codeHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @ColumnDefault("0")
  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  /** SHA-256 of the ticket handed out once the code was verified */
  @Column(name = "ticket_hash", length = 64)
  private String ticketHash;

  @Column(name = "ticket_expires_at")
  private Instant ticketExpiresAt;

  /** Set when the ticket was used, or when a newer code replaced this one */
  @Column(name = "consumed_at")
  private Instant consumedAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
