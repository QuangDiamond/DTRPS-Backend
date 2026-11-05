package com.dtrps.modules.poi.dto;

import com.dtrps.modules.poi.entity.PlaceCategory;

/** A code + display name pair, e.g. a province or a category subtype. */
public record ReferenceItem(String code, String name) {

  /** The type itself, or null when there is none. */
  public static ReferenceItem typeOf(PlaceCategory type) {
    return type == null ? null : new ReferenceItem(type.getCode(), type.getName());
  }

  /** The service group the type belongs to, or null. */
  public static ReferenceItem groupOf(PlaceCategory type) {
    return type == null ? null : typeOf(type.getParent());
  }
}
