package com.dtrps.modules.user.entity;

import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.SpecialNeed;
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
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "preferences")
@Getter
@Setter
public class Preference {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "preference_id")
  private Long preferenceId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true)
  private User user;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Experience types (place subtypes) the traveller is interested in. */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "preference_interest_categories",
      indexes = @Index(name = "idx_preference_interest_categories", columnList = "category_id"),
      joinColumns = @JoinColumn(name = "preference_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  private Set<PlaceCategory> interests = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "preference_special_needs",
      indexes = @Index(name = "idx_preference_special_need", columnList = "special_need_id"),
      joinColumns = @JoinColumn(name = "preference_id"),
      inverseJoinColumns = @JoinColumn(name = "special_need_id"))
  private Set<SpecialNeed> specialNeeds = new HashSet<>();
}
