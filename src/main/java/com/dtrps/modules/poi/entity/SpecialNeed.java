package com.dtrps.modules.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "special_needs")
@Getter
@Setter
public class SpecialNeed {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "special_need_id")
  private Long specialNeedId;

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "name", nullable = false, unique = true, length = 100)
  private String name;
}
