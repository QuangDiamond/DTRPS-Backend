package com.dtrps.modules.poi.dto;

import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.service.ReferenceDataService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every service group and type of a place. The main type (the first one ticked) comes first, the
 * rest follow the fixed group order; each group is listed once.
 */
public record PlaceTypes(List<ReferenceItem> groups, List<PlaceTypes.Type> categories) {

  /** A type and the service group it belongs to */
  public record Type(String code, String name, String groupCode) {
    static Type of(PlaceCategory type) {
      return new Type(
          type.getCode(),
          type.getName(),
          type.getParent() == null ? null : type.getParent().getCode());
    }
  }

  public static PlaceTypes of(PlaceCategory main, Collection<PlaceCategory> all) {
    List<PlaceCategory> types = new ArrayList<>();
    if (main != null) {
      types.add(main);
    }
    all.stream()
        .filter(Objects::nonNull)
        .filter(type -> main == null || !type.getCategoryId().equals(main.getCategoryId()))
        .sorted(
            Comparator.comparingInt(PlaceTypes::groupRank)
                .thenComparing(PlaceCategory::getCategoryId))
        .forEach(types::add);
    Map<String, ReferenceItem> groups = new LinkedHashMap<>();
    for (PlaceCategory type : types) {
      ReferenceItem group = ReferenceItem.groupOf(type);
      if (group != null) {
        groups.putIfAbsent(group.code(), group);
      }
    }
    return new PlaceTypes(List.copyOf(groups.values()), types.stream().map(Type::of).toList());
  }

  private static int groupRank(PlaceCategory type) {
    int rank =
        type.getParent() == null
            ? -1
            : ReferenceDataService.GROUP_ORDER.indexOf(type.getParent().getCode());
    return rank < 0 ? Integer.MAX_VALUE : rank;
  }
}
