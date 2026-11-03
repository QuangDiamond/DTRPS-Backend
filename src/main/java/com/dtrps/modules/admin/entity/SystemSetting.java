package com.dtrps.modules.admin.entity;

import com.dtrps.modules.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

/** Platform settings edited on the Admin Web settings page, one row per key. */
@Entity
@Table(
    name = "system_settings",
    indexes = {@Index(name = "idx_system_settings_updated_by", columnList = "updated_by")})
@Getter
@Setter
public class SystemSetting {
  @Id
  @Column(name = "setting_key", length = 100)
  private String key;

  @Column(name = "setting_value", nullable = false, columnDefinition = "text")
  private String value;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by")
  private User updatedBy;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
