package com.dtrps.modules.poi.service;

import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Place filters that match any of a place's types, not just the main one. */
final class PlaceTypeFilter {
  private PlaceTypeFilter() {}

  /**
   * The place has a type with this code ({@code level} null), or a type in the group with this code
   * ({@code level} "parent"). An EXISTS subquery, so a place with several types appears once.
   */
  static Predicate hasType(
      Root<Place> root,
      CommonAbstractCriteria query,
      CriteriaBuilder cb,
      String level,
      String code) {
    Subquery<Long> owned = query.subquery(Long.class);
    Root<Place> place = owned.from(Place.class);
    Join<Place, PlaceCategory> type = place.join("categories");
    var matched = level == null ? type.get("code") : type.get(level).get("code");
    owned.select(place.get("placeId")).where(cb.equal(place, root), cb.equal(matched, code));
    return cb.exists(owned);
  }
}
