package com.dtrps.modules.user.dto;

import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.SpecialNeed;
import com.dtrps.modules.user.entity.Preference;
import java.util.List;

public record PreferenceResponse(
    String notes, List<String> interests, List<String> specialNeeds) {
  public static PreferenceResponse empty() {
    return new PreferenceResponse(null, List.of(), List.of());
  }

  public static PreferenceResponse from(Preference preference) {
    return new PreferenceResponse(
        preference.getNotes(),
        preference.getInterests().stream().map(PlaceCategory::getCode).sorted().toList(),
        preference.getSpecialNeeds().stream().map(SpecialNeed::getCode).sorted().toList());
  }
}
