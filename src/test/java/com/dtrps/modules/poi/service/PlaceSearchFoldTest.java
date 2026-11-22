package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Tìm địa điểm theo tên không phân biệt dấu/hoa thường. */
class PlaceSearchFoldTest {

  @Test
  void foldsVietnameseAccentsAndCase() {
    assertThat(PlaceService.foldVietnamese("Chùa Hương")).isEqualTo("chua huong");
    assertThat(PlaceService.foldVietnamese("ĐÀ LẠT")).isEqualTo("da lat");
    assertThat(PlaceService.foldVietnamese("Hồ Tây")).isEqualTo("ho tay");
    assertThat(PlaceService.foldVietnamese("Phố đường tàu")).isEqualTo("pho duong tau");
    assertThat(PlaceService.foldVietnamese("chua huong")).isEqualTo("chua huong");
  }

  @Test
  void translateTablesHaveMatchingLengths() {
    assertThat(PlaceService.VI_FROM).hasSameSizeAs(PlaceService.VI_TO);
  }
}
