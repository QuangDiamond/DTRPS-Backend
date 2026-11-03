package com.dtrps.modules.poi.entity;

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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

/**
 * Two-level tree: the 4 service groups (lodging, fnb, attraction, shopping) have no parent, and
 * every subtype points to its group. Places reference a subtype.
 */
@Entity
@Table(
    name = "place_categories",
    indexes = {@Index(name = "idx_place_categories_parent", columnList = "parent_id")})
@Check(name = "chk_place_category_status", constraints = "status IN ('ACTIVE', 'INACTIVE')")
@Getter
@Setter
public class PlaceCategory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "category_id")
  private Long categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private PlaceCategory parent;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "name", nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @ColumnDefault("'ACTIVE'")
  @Column(name = "status", nullable = false, length = 30)
  private String status = "ACTIVE";
}
