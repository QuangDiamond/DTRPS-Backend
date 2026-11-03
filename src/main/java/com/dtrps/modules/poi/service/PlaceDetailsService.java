package com.dtrps.modules.poi.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and replaces a place's opening hours, prices and photos as one set. */
@Service
public class PlaceDetailsService {
  public static final int MAX_IMAGES = 10;
  private static final String DEFAULT_CURRENCY = "VND";

  private static final String DUPLICATE_DAY = "Mỗi ngày trong tuần chỉ khai báo giờ mở cửa một lần";
  private static final String MISSING_TIMES = "Ngày mở cửa cần có cả giờ mở và giờ đóng";
  private static final String SAME_TIMES = "Giờ mở cửa và giờ đóng cửa không được trùng nhau";
  private static final String TOO_MANY_IMAGES = "Tối đa " + MAX_IMAGES + " ảnh cho một địa điểm";
  private static final String MANY_COVERS = "Chỉ chọn một ảnh bìa";
  private static final String DUPLICATE_IMAGE = "Ảnh bị trùng";

  private final PlaceOpeningHourRepository hourRepository;
  private final PlacePriceRepository priceRepository;
  private final PlaceImageRepository imageRepository;

  public PlaceDetailsService(
      PlaceOpeningHourRepository hourRepository,
      PlacePriceRepository priceRepository,
      PlaceImageRepository imageRepository) {
    this.hourRepository = hourRepository;
    this.priceRepository = priceRepository;
    this.imageRepository = imageRepository;
  }

  @Transactional(readOnly = true)
  public PlaceDetails read(Long placeId) {
    List<OpeningHourDto> hours =
        hourRepository.findByPlacePlaceIdOrderByDayOfWeekAsc(placeId).stream()
            .map(
                hour ->
                    new OpeningHourDto(
                        hour.getDayOfWeek().intValue(),
                        format(hour.getOpenTime()),
                        format(hour.getCloseTime()),
                        Boolean.TRUE.equals(hour.getIsClosed())))
            .toList();
    List<PriceDto> prices =
        priceRepository.findByPlacePlaceIdOrderByPriceIdAsc(placeId).stream()
            .map(
                price ->
                    new PriceDto(
                        price.getPriceType(),
                        price.getAmount(),
                        price.getCurrency(),
                        price.getDescription()))
            .toList();
    List<ImageDto> images =
        imageRepository.findByPlacePlaceIdOrderByIsPrimaryDescImageIdAsc(placeId).stream()
            .map(
                image ->
                    new ImageDto(image.getImageUrl(), Boolean.TRUE.equals(image.getIsPrimary())))
            .toList();
    return new PlaceDetails(hours, prices, images);
  }

  /**
   * Checks the rules a single field annotation cannot express, and returns a cleaned copy (missing
   * lists become empty, the first photo becomes the cover when none is chosen).
   */
  public PlaceDetails normalize(
      List<OpeningHourDto> hours, List<PriceDto> prices, List<ImageDto> images) {
    List<OpeningHourDto> cleanHours = new ArrayList<>();
    Set<Integer> days = new HashSet<>();
    for (OpeningHourDto hour : nullToEmpty(hours)) {
      if (!days.add(hour.dayOfWeek())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, DUPLICATE_DAY);
      }
      if (hour.closed()) {
        cleanHours.add(new OpeningHourDto(hour.dayOfWeek(), null, null, true));
        continue;
      }
      if (hour.openTime() == null || hour.closeTime() == null) {
        throw new ApiException(HttpStatus.BAD_REQUEST, MISSING_TIMES);
      }
      // Closing after midnight (e.g. 18:00 – 02:00) is allowed
      if (hour.openTime().equals(hour.closeTime())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, SAME_TIMES);
      }
      cleanHours.add(hour);
    }
    cleanHours.sort((a, b) -> a.dayOfWeek() - b.dayOfWeek());

    List<PriceDto> cleanPrices =
        nullToEmpty(prices).stream()
            .map(
                price ->
                    new PriceDto(
                        price.priceType().trim().toUpperCase(Locale.ROOT),
                        price.amount(),
                        price.currency() == null ? DEFAULT_CURRENCY : price.currency(),
                        blankToNull(price.description())))
            .toList();

    List<ImageDto> source = nullToEmpty(images);
    if (source.size() > MAX_IMAGES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY_IMAGES);
    }
    if (source.stream().filter(ImageDto::primary).count() > 1) {
      throw new ApiException(HttpStatus.BAD_REQUEST, MANY_COVERS);
    }
    if (source.stream().map(ImageDto::url).distinct().count() < source.size()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, DUPLICATE_IMAGE);
    }
    boolean hasCover = source.stream().anyMatch(ImageDto::primary);
    List<ImageDto> cleanImages = new ArrayList<>();
    for (int index = 0; index < source.size(); index++) {
      ImageDto image = source.get(index);
      cleanImages.add(
          new ImageDto(image.url().trim(), image.primary() || (!hasCover && index == 0)));
    }
    return new PlaceDetails(cleanHours, cleanPrices, cleanImages);
  }

  /** Replaces all hours, prices and photos of the place. Expects {@link #normalize} output. */
  @Transactional
  public void replace(Place place, PlaceDetails details) {
    Long placeId = place.getPlaceId();
    hourRepository.deleteByPlaceId(placeId);
    priceRepository.deleteByPlaceId(placeId);
    imageRepository.deleteByPlaceId(placeId);

    for (OpeningHourDto dto : details.openingHours()) {
      PlaceOpeningHour hour = new PlaceOpeningHour();
      hour.setPlace(place);
      hour.setDayOfWeek(dto.dayOfWeek().shortValue());
      hour.setOpenTime(dto.openTime() == null ? null : LocalTime.parse(dto.openTime()));
      hour.setCloseTime(dto.closeTime() == null ? null : LocalTime.parse(dto.closeTime()));
      hour.setIsClosed(dto.closed());
      hourRepository.save(hour);
    }
    for (PriceDto dto : details.prices()) {
      PlacePrice price = new PlacePrice();
      price.setPlace(place);
      price.setPriceType(dto.priceType());
      price.setAmount(dto.amount());
      price.setCurrency(dto.currency());
      price.setDescription(dto.description());
      priceRepository.save(price);
    }
    for (ImageDto dto : details.images()) {
      PlaceImage image = new PlaceImage();
      image.setPlace(place);
      image.setImageUrl(dto.url());
      image.setIsPrimary(dto.primary());
      imageRepository.save(image);
    }
  }

  private static String format(LocalTime time) {
    return time == null ? null : time.toString().substring(0, 5);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static <T> List<T> nullToEmpty(List<T> list) {
    return list == null ? List.of() : list;
  }
}
