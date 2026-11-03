package com.dtrps.modules.poi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "place_opening_hours",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_place_opening_day",
          columnNames = {"place_id", "day_of_week"})
    })
@Check(name = "chk_day_of_week", constraints = "(((day_of_week >= 1) AND (day_of_week <= 7)))")
@Getter
@Setter
public class PlaceOpeningHour {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "opening_hour_id")
  private Long openingHourId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "place_id", nullable = false)
  private Place place;

  @Column(name = "day_of_week", nullable = false)
  private Short dayOfWeek;

  @Column(name = "open_time")
  private LocalTime openTime;

  @Column(name = "close_time")
  private LocalTime closeTime;

  @ColumnDefault("false")
  @Column(name = "is_closed", nullable = false)
  private Boolean isClosed = false;
}
