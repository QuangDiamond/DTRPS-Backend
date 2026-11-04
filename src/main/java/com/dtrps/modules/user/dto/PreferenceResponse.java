package com.dtrps.modules.user.dto;

import com.dtrps.modules.poi.entity.Interest;
import com.dtrps.modules.poi.entity.SpecialNeed;
import com.dtrps.modules.user.entity.Preference;
import java.util.List;

public record PreferenceResponse(
    String budgetLevel,
    String travelStyle,
    String notes,
    List<String> interests,
    List<String> specialNeeds) {
  public static PreferenceResponse empty() {
    return new PreferenceResponse(null, null, null, List.of(), List.of());
  }

  public static PreferenceResponse from(Preference preference) {
    return new PreferenceResponse(
        preference.getBudgetLevel(),
        preference.getTravelStyle(),
        preference.getNotes(),
        preference.getInterests().stream().map(Interest::getCode).sorted().toList(),
        preference.getSpecialNeeds().stream().map(SpecialNeed::getCode).sorted().toList());
  }
}
