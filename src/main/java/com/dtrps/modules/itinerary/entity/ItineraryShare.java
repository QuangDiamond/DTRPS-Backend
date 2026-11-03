package com.dtrps.modules.itinerary.entity;

import com.dtrps.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "itinerary_shares",
    indexes = {
      @Index(name = "idx_itinerary_shares_creator", columnList = "created_by"),
      @Index(name = "idx_itinerary_shares_itinerary", columnList = "itinerary_id")
    })
@Check(name = "chk_itinerary_share_access", constraints = "access_type IN ('VIEW', 'EDIT')")
@Getter
@Setter
public class ItineraryShare {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "share_id")
  private Long shareId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "itinerary_id", nullable = false)
  private Itinerary itinerary;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(name = "share_token", nullable = false, unique = true)
  private UUID shareToken;

  @ColumnDefault("'VIEW'")
  @Column(name = "access_type", nullable = false, length = 30)
  private String accessType = "VIEW";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
