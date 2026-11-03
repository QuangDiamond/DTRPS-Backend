package com.dtrps.modules.user.entity;

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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "notifications",
    indexes = {@Index(name = "idx_notifications_user_id", columnList = "user_id")})
@Getter
@Setter
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "notification_id")
  private Long notificationId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "type", nullable = false, length = 50)
  private String type;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  /** Page to open when the notification is clicked, e.g. /admin/listings/12 */
  @Column(name = "link", length = 500)
  private String link;

  @ColumnDefault("false")
  @Column(name = "is_read", nullable = false)
  private Boolean isRead = false;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
