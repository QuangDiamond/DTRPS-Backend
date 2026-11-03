package com.dtrps.modules.poi.dto;

import java.util.List;

/** A service group with every one of its types, inactive ones included. */
public record AdminCategoryGroup(
    Long id, String code, String name, String status, List<AdminCategory> types) {

  /** {@code placeCount}: places currently using this type. */
  public record AdminCategory(
      Long id, String code, String name, String description, String status, long placeCount) {}
}
