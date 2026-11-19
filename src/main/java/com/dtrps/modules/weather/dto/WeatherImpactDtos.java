package com.dtrps.modules.weather.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** UC-045 Weather Impact: warn about outdoor activities hit by bad weather, suggest — never apply
 * automatically — adjustments. */
public final class WeatherImpactDtos {
  private WeatherImpactDtos() {}

  public enum SuggestionType {
    SHIFT_TIME,
    CHANGE_DAY,
    SWAP_PLACE
  }

  public record Suggestion(
      SuggestionType type,
      String description,
      LocalDate newDate,
      LocalTime newStartTime,
      LocalTime newEndTime,
      Long newPlaceId,
      String newPlaceName) {}

  public record ImpactedItem(
      Long itemId,
      Long dayId,
      Integer dayNumber,
      LocalDate date,
      Long placeId,
      String placeName,
      LocalTime startTime,
      LocalTime endTime,
      Integer weatherCode,
      String condition,
      String warningMessage,
      List<Suggestion> suggestions) {}

  public record ImpactResponse(Long itineraryId, List<ImpactedItem> impactedItems) {}

  /** Khách chọn 1 đề xuất rồi gửi lại — chỉ áp dụng khi có request này, không tự động sửa lịch. */
  public record ApplyRequest(
      @NotNull @Positive Long itemId,
      @NotBlank String suggestionType,
      LocalDate newDate,
      LocalTime newStartTime,
      LocalTime newEndTime,
      Long newPlaceId) {}
}
