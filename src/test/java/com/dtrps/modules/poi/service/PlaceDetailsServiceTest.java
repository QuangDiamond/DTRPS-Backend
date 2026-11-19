package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.lenient;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.SystemSettingsService;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test PlaceDetailsService.normalize — sheet Poi, UTC024–UTC029 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class PlaceDetailsServiceTest {
  @Mock private PlaceOpeningHourRepository hours;
  @Mock private PlacePriceRepository prices;
  @Mock private PlaceImageRepository images;
  @Mock private SystemSettingsService settings;

  private PlaceDetailsService service;

  @BeforeEach
  void setUp() {
    service = new PlaceDetailsService(hours, prices, images, settings);
    lenient().when(settings.maxPlaceImages()).thenReturn(10);
  }

  private static OpeningHourDto day(int day, String open, String close) {
    return new OpeningHourDto(day, open, close, false);
  }

  private static ImageDto image(String name, boolean primary) {
    return new ImageDto(" https://res.cloudinary.com/x/" + name + ".jpg ", primary);
  }

  private ApiException error(List<OpeningHourDto> hours, List<ImageDto> images) {
    return catchThrowableOfType(() -> service.normalize(hours, null, images), ApiException.class);
  }

  @Test
  @DisplayName("UTC024 7 valid days, 3 images without cover → sorted days, first image is cover")
  void valid_cleaned() {
    List<OpeningHourDto> week =
        IntStream.iterate(7, d -> d >= 1, d -> d - 1)
            .mapToObj(d -> day(d, "08:00", "17:00"))
            .toList();

    PlaceDetails details =
        service.normalize(
            week, null, List.of(image("a", false), image("b", false), image("c", false)));

    assertThat(details.openingHours())
        .extracting(OpeningHourDto::dayOfWeek)
        .containsExactly(1, 2, 3, 4, 5, 6, 7);
    assertThat(details.prices()).isEmpty();
    assertThat(details.images()).extracting(ImageDto::primary).containsExactly(true, false, false);
    assertThat(details.images().get(0).url()).isEqualTo("https://res.cloudinary.com/x/a.jpg");
  }

  @Test
  @DisplayName("UTC025 Monday declared twice → 400")
  void duplicateDay_badRequest() {
    ApiException e = error(List.of(day(1, "08:00", "17:00"), day(1, "09:00", "18:00")), null);

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Mỗi ngày trong tuần chỉ khai báo giờ mở cửa một lần");
  }

  @Test
  @DisplayName("UTC026 Tuesday open without closing time → 400")
  void missingClose_badRequest() {
    ApiException e = error(List.of(day(2, "08:00", null)), null);

    assertThat(e.getMessage()).isEqualTo("Ngày mở cửa cần có cả giờ mở và giờ đóng");
  }

  @Test
  @DisplayName("UTC027 openTime = closeTime = 08:00 → 400; a closed day needs no times")
  void sameTimes_badRequest() {
    ApiException e = error(List.of(day(3, "08:00", "08:00")), null);

    assertThat(e.getMessage()).isEqualTo("Giờ mở cửa và giờ đóng cửa không được trùng nhau");
    assertThat(
            service
                .normalize(List.of(new OpeningHourDto(3, null, null, true)), null, null)
                .openingHours())
        .hasSize(1);
  }

  @Test
  @DisplayName("UTC028 Two images marked as cover → 400")
  void twoCovers_badRequest() {
    ApiException e = error(null, List.of(image("a", true), image("b", true)));

    assertThat(e.getMessage()).isEqualTo("Chỉ chọn một ảnh bìa");
  }

  @Test
  @DisplayName("UTC029 Two images with the same URL → 400")
  void duplicateImage_badRequest() {
    ApiException e = error(null, List.of(image("a", false), image("a", false)));

    assertThat(e.getMessage()).isEqualTo("Ảnh bị trùng");
  }

  @Test
  @DisplayName("More images than the admin setting allows → 400")
  void tooManyImages_badRequest() {
    lenient().when(settings.maxPlaceImages()).thenReturn(2);

    ApiException e = error(null, List.of(image("a", false), image("b", false), image("c", false)));

    assertThat(e.getMessage()).isEqualTo("Tối đa 2 ảnh cho một địa điểm");
  }
}
