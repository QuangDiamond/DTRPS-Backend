package com.dtrps.modules.user.entity;

import com.dtrps.modules.poi.entity.Province;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "users",
    indexes = {@Index(name = "idx_users_province", columnList = "province_id")})
@Check(name = "chk_user_status", constraints = "status IN ('ACTIVE', 'SUSPENDED')")
@Getter
@Setter
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Column(name = "email", nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "avatar_url", columnDefinition = "text")
  private String avatarUrl;

  /** Home province, optional */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "province_id")
  private Province province;

  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "ACTIVE";

  @CreationTimestamp
  @ColumnDefault("now()")
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_roles",
      indexes = @Index(name = "idx_user_roles_role", columnList = "role_id"),
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @BatchSize(size = 50)
  private Set<Role> roles = new HashSet<>();
}
