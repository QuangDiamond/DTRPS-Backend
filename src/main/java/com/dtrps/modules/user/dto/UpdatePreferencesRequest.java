package com.dtrps.modules.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdatePreferencesRequest(
    @Pattern(regexp = "SAVING|MEDIUM|LUXURY", message = "Mức ngân sách không hợp lệ")
        String budgetLevel,
    @Pattern(
            regexp = "RELAX|ADVENTURE|CULTURE|FAMILY|SOLO|LUXURY",
            message = "Phong cách du lịch không hợp lệ")
        String travelStyle,
    @Size(max = 2000, message = "Ghi chú quá dài") String notes,
    @Size(max = 50, message = "Chọn quá nhiều mục") Set<String> interests,
    @Size(max = 50, message = "Chọn quá nhiều mục") Set<String> specialNeeds) {
  public UpdatePreferencesRequest {
    interests = interests == null ? Set.of() : Set.copyOf(interests);
    specialNeeds = specialNeeds == null ? Set.of() : Set.copyOf(specialNeeds);
  }
}
