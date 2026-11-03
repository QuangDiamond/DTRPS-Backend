package com.dtrps.modules.user.entity;

import com.dtrps.modules.poi.entity.Interest;
import com.dtrps.modules.poi.entity.SpecialNeed;
import com.dtrps.modules.poi.entity.TransportMode;
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
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "preferences")
@Check(
    name = "chk_preference_budget_level",
    constraints = "budget_level IN ('SAVING', 'MEDIUM', 'LUXURY')")
@Check(
    name = "chk_preference_travel_style",
    constraints = "travel_style IN ('RELAX', 'ADVENTURE', 'CULTURE', 'FAMILY', 'SOLO', 'LUXURY')")
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

  @Column(name = "travel_style", length = 100)
  private String travelStyle;

  @Column(name = "budget_level", length = 50)
  private String budgetLevel;

  @Column(name = "notes", columnDefinition = "text")
  private String notes;

  @UpdateTimestamp
  @ColumnDefault("now()")
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "preference_interests",
      indexes = @Index(name = "idx_preference_interests_interest", columnList = "interest_id"),
      joinColumns = @JoinColumn(name = "preference_id"),
      inverseJoinColumns = @JoinColumn(name = "interest_id"))
  private Set<Interest> interests = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "preference_transport_modes",
      indexes = @Index(name = "idx_preference_transport_mode", columnList = "transport_mode_id"),
      joinColumns = @JoinColumn(name = "preference_id"),
      inverseJoinColumns = @JoinColumn(name = "transport_mode_id"))
  private Set<TransportMode> transportModes = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "preference_special_needs",
      indexes = @Index(name = "idx_preference_special_need", columnList = "special_need_id"),
      joinColumns = @JoinColumn(name = "preference_id"),
      inverseJoinColumns = @JoinColumn(name = "special_need_id"))
  private Set<SpecialNeed> specialNeeds = new HashSet<>();
}
