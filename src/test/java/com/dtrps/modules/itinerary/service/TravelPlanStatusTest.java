package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TravelPlanStatusTest {

  @ParameterizedTest(name = "{0} -> {1} = {2}")
  @CsvSource({
    // Đi tiếp đúng thứ tự
    "DRAFT, PLANNED, true",
    "PLANNED, IN_PROGRESS, true",
    "IN_PROGRESS, COMPLETED, true",
    // Hủy ở mọi bước trước COMPLETED
    "DRAFT, CANCELLED, true",
    "PLANNED, CANCELLED, true",
    "IN_PROGRESS, CANCELLED, true",
    // Nhảy cóc
    "DRAFT, IN_PROGRESS, false",
    "DRAFT, COMPLETED, false",
    "PLANNED, COMPLETED, false",
    // Đi ngược
    "PLANNED, DRAFT, false",
    "IN_PROGRESS, PLANNED, false",
    "IN_PROGRESS, DRAFT, false",
    "COMPLETED, IN_PROGRESS, false",
    "CANCELLED, DRAFT, false",
    // Trạng thái cuối
    "COMPLETED, CANCELLED, false",
    "CANCELLED, PLANNED, false",
    // Giữ nguyên
    "DRAFT, DRAFT, false",
    "COMPLETED, COMPLETED, false",
    // Giá trị lạ
    "DRAFT, ARCHIVED, false",
    "UNKNOWN, PLANNED, false"
  })
  void canTransition(String from, String to, boolean expected) {
    assertThat(TravelPlanStatus.canTransition(from, to)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Chỉ sửa được khi DRAFT hoặc PLANNED")
  void editable() {
    assertThat(TravelPlanStatus.ALL.stream().filter(TravelPlanStatus::isEditable))
        .containsExactly("DRAFT", "PLANNED");
  }

  @Test
  @DisplayName("Xóa được ở mọi trạng thái trừ IN_PROGRESS")
  void deletable() {
    assertThat(TravelPlanStatus.ALL.stream().filter(s -> !TravelPlanStatus.isDeletable(s)))
        .containsExactly("IN_PROGRESS");
  }

  @Test
  void allowedNextOfNullIsEmpty() {
    assertThat(TravelPlanStatus.allowedNext(null)).isEmpty();
    assertThat(TravelPlanStatus.isKnown(null)).isFalse();
  }
}
