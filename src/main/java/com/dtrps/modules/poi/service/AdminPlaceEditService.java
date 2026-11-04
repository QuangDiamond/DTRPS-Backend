package com.dtrps.modules.poi.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.poi.dto.AdminPlaceDetail;
import com.dtrps.modules.poi.dto.AdminPlaceRequest;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.entity.PlaceImage;
import com.dtrps.modules.poi.entity.PlaceOpeningHour;
import com.dtrps.modules.poi.entity.PlacePrice;
import com.dtrps.modules.poi.repository.PlaceImageRepository;
import com.dtrps.modules.poi.repository.PlaceOpeningHourRepository;
import com.dtrps.modules.poi.repository.PlacePriceRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Places the admin adds and maintains directly: no partner, no review, public as soon as they are
 * ACTIVE. Partner places are refused here; they change only through partner revisions.
 */
@Service
public class AdminPlaceEditService {
  static final String AUDIT_ENTITY = "places";
  static final String ACTION_CREATED = "PLACE_CREATED";
  static final String ACTION_UPDATED = "PLACE_UPDATED";
  static final String ACTION_STATUS = "PLACE_STATUS_CHANGED";
  static final String ACTION_HOURS = "PLACE_HOURS_UPDATED";
  static final String ACTION_IMAGES = "PLACE_IMAGES_UPDATED";
  static final String ACTION_PRICES = "PLACE_PRICES_UPDATED";

  private static final String PARTNER_PLACE =
      "Địa điểm của đối tác chỉ thay đổi qua bản sửa do đối tác gửi";
  private static final String FULL_WEEK =
      "Vui lòng gửi giờ mở cửa của cả 7 ngày (Thứ 2 đến Chủ nhật)";
  private static final String TOO_MANY_IMAGES =
      "Tối đa " + PlaceDetailsService.MAX_IMAGES + " ảnh cho một địa điểm";
  private static final String DUPLICATE_IMAGE = "Ảnh này đã có trong địa điểm";
  private static final String IMAGE_NOT_FOUND = "Không tìm thấy ảnh";
  private static final String PRICE_NOT_FOUND = "Không tìm thấy mức giá";
  private static final int MAX_PRICES = 20;
  private static final String TOO_MANY_PRICES = "Tối đa " + MAX_PRICES + " mức giá";
  private static final String DEFAULT_CURRENCY = "VND";

  /** New places stay hidden until the admin publishes them (status ACTIVE) */
  private static final String DRAFT = "DRAFT";

  private static final int WEEK = 7;

  private final PlaceRepository placeRepository;
  private final PlaceImageRepository imageRepository;
  private final PlacePriceRepository priceRepository;
  private final PlaceOpeningHourRepository hourRepository;
  private final BusinessListingRepository listingRepository;
  private final PlaceDetailsService placeDetails;
  private final PlaceReferenceResolver references;
  private final AdminPlaceService catalogue;
  private final AuditService auditService;
  private final Clock clock;

  public AdminPlaceEditService(
      PlaceRepository placeRepository,
      PlaceImageRepository imageRepository,
      PlacePriceRepository priceRepository,
      PlaceOpeningHourRepository hourRepository,
      BusinessListingRepository listingRepository,
      PlaceDetailsService placeDetails,
      PlaceReferenceResolver references,
      AdminPlaceService catalogue,
      AuditService auditService,
      Clock clock) {
    this.placeRepository = placeRepository;
    this.imageRepository = imageRepository;
    this.priceRepository = priceRepository;
    this.hourRepository = hourRepository;
    this.listingRepository = listingRepository;
    this.placeDetails = placeDetails;
    this.references = references;
    this.catalogue = catalogue;
    this.auditService = auditService;
    this.clock = clock;
  }

  // ===== Whole place =====

  @Transactional
  public AdminPlaceDetail create(Long adminId, AdminPlaceRequest request) {
    PlaceDetails details = normalize(request.openingHours(), request.prices(), request.images());
    Place place = new Place();
    applyBasics(place, request);
    place.setStatus(request.status() == null ? DRAFT : request.status());
    placeRepository.save(place);
    placeDetails.replace(place, details);
    placeRepository.flush();
    auditService.record(adminId, ACTION_CREATED, AUDIT_ENTITY, place.getPlaceId());
    return catalogue.get(place.getPlaceId());
  }

  /** Lists left out (null) keep what the place already has. */
  @Transactional
  public AdminPlaceDetail update(Long adminId, Long placeId, AdminPlaceRequest request) {
    Place place = editable(placeId);
    boolean replaceDetails =
        request.openingHours() != null || request.prices() != null || request.images() != null;
    PlaceDetails details = null;
    if (replaceDetails) {
      PlaceDetails current = placeDetails.read(placeId);
      details =
          normalize(
              request.openingHours() == null ? current.openingHours() : request.openingHours(),
              request.prices() == null ? current.prices() : request.prices(),
              request.images() == null ? current.images() : request.images());
    }
    applyBasics(place, request);
    if (request.status() != null) {
      place.setStatus(request.status());
    }
    if (replaceDetails) {
      placeDetails.replace(place, details);
    }
    touch(place);
    auditService.record(adminId, ACTION_UPDATED, AUDIT_ENTITY, placeId);
    return catalogue.get(placeId);
  }

  @Transactional
  public AdminPlaceDetail changeStatus(Long adminId, Long placeId, String status) {
    Place place = editable(placeId);
    String previous = place.getStatus();
    place.setStatus(status);
    placeRepository.flush();
    auditService.record(adminId, ACTION_STATUS, AUDIT_ENTITY, placeId, previous + " -> " + status);
    return catalogue.get(placeId);
  }

  // ===== Opening hours =====

  /** Replaces the whole week: days 1–7 each once, a closed day flagged {@code closed}. */
  @Transactional
  public AdminPlaceDetail replaceHours(Long adminId, Long placeId, List<OpeningHourDto> days) {
    Place place = editable(placeId);
    List<OpeningHourDto> hours = normalize(days, null, null).openingHours();
    if (hours.size() != WEEK) {
      throw new ApiException(HttpStatus.BAD_REQUEST, FULL_WEEK);
    }
    hourRepository.deleteByPlaceId(placeId);
    for (OpeningHourDto dto : hours) {
      PlaceOpeningHour hour = new PlaceOpeningHour();
      hour.setPlace(place);
      hour.setDayOfWeek(dto.dayOfWeek().shortValue());
      hour.setOpenTime(dto.openTime() == null ? null : LocalTime.parse(dto.openTime()));
      hour.setCloseTime(dto.closeTime() == null ? null : LocalTime.parse(dto.closeTime()));
      hour.setIsClosed(dto.closed());
      hourRepository.save(hour);
    }
    touch(place);
    auditService.record(adminId, ACTION_HOURS, AUDIT_ENTITY, placeId);
    return catalogue.get(placeId);
  }

  // ===== Photos =====

  /** Adds an https photo. The first photo, or one marked primary, becomes the cover. */
  @Transactional
  public AdminPlaceDetail addImage(Long adminId, Long placeId, ImageDto request) {
    Place place = editable(placeId);
    String url = request.url().trim();
    long count = imageRepository.countByPlacePlaceId(placeId);
    if (count >= PlaceDetailsService.MAX_IMAGES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY_IMAGES);
    }
    if (imageRepository.existsByPlacePlaceIdAndImageUrl(placeId, url)) {
      throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_IMAGE);
    }
    boolean primary = request.primary() || count == 0;
    if (primary) {
      imageRepository.clearPrimary(placeId);
    }
    PlaceImage image = new PlaceImage();
    image.setPlace(place);
    image.setImageUrl(url);
    image.setIsPrimary(primary);
    imageRepository.save(image);
    touch(place);
    auditService.record(adminId, ACTION_IMAGES, AUDIT_ENTITY, placeId, "Thêm ảnh");
    return catalogue.get(placeId);
  }

  /** Deleting the cover promotes the earliest remaining photo. */
  @Transactional
  public AdminPlaceDetail deleteImage(Long adminId, Long placeId, Long imageId) {
    Place place = editable(placeId);
    PlaceImage image = image(placeId, imageId);
    boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
    imageRepository.delete(image);
    imageRepository.flush();
    if (wasPrimary) {
      imageRepository
          .findFirstByPlacePlaceIdOrderByImageIdAsc(placeId)
          .ifPresent(next -> next.setIsPrimary(true));
    }
    touch(place);
    auditService.record(adminId, ACTION_IMAGES, AUDIT_ENTITY, placeId, "Xoá ảnh");
    return catalogue.get(placeId);
  }

  /**
   * The old cover is cleared before the new one is set, in the same transaction, because the
   * database allows a single cover per place (uq_place_primary_image).
   */
  @Transactional
  public AdminPlaceDetail setPrimaryImage(Long adminId, Long placeId, Long imageId) {
    Place place = editable(placeId);
    PlaceImage image = image(placeId, imageId);
    if (!Boolean.TRUE.equals(image.getIsPrimary())) {
      imageRepository.clearPrimary(placeId);
      image.setIsPrimary(true);
      imageRepository.flush();
    }
    touch(place);
    auditService.record(adminId, ACTION_IMAGES, AUDIT_ENTITY, placeId, "Đặt ảnh chính");
    return catalogue.get(placeId);
  }

  // ===== Prices =====

  @Transactional
  public AdminPlaceDetail addPrice(Long adminId, Long placeId, PriceDto request) {
    Place place = editable(placeId);
    if (priceRepository.countByPlacePlaceId(placeId) >= MAX_PRICES) {
      throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY_PRICES);
    }
    PlacePrice price = new PlacePrice();
    price.setPlace(place);
    applyPrice(price, request);
    priceRepository.save(price);
    touch(place);
    auditService.record(adminId, ACTION_PRICES, AUDIT_ENTITY, placeId, "Thêm giá");
    return catalogue.get(placeId);
  }

  @Transactional
  public AdminPlaceDetail updatePrice(Long adminId, Long placeId, Long priceId, PriceDto request) {
    Place place = editable(placeId);
    applyPrice(price(placeId, priceId), request);
    touch(place);
    auditService.record(adminId, ACTION_PRICES, AUDIT_ENTITY, placeId, "Sửa giá");
    return catalogue.get(placeId);
  }

  @Transactional
  public AdminPlaceDetail deletePrice(Long adminId, Long placeId, Long priceId) {
    Place place = editable(placeId);
    priceRepository.delete(price(placeId, priceId));
    touch(place);
    auditService.record(adminId, ACTION_PRICES, AUDIT_ENTITY, placeId, "Xoá giá");
    return catalogue.get(placeId);
  }

  // ===== Helpers =====

  /** The place, if it exists and no partner owns it. */
  private Place editable(Long placeId) {
    Place place =
        placeRepository
            .findById(placeId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, AdminPlaceService.PLACE_NOT_FOUND));
    if (listingRepository.existsByPlacePlaceId(placeId)) {
      throw new ApiException(HttpStatus.CONFLICT, PARTNER_PLACE);
    }
    return place;
  }

  private PlaceImage image(Long placeId, Long imageId) {
    return imageRepository
        .findByImageIdAndPlacePlaceId(imageId, placeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, IMAGE_NOT_FOUND));
  }

  private PlacePrice price(Long placeId, Long priceId) {
    return priceRepository
        .findByPriceIdAndPlacePlaceId(priceId, placeId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PRICE_NOT_FOUND));
  }

  /**
   * Shared checks of {@link PlaceDetailsService#normalize}, plus: hours list all 7 days or none.
   */
  private PlaceDetails normalize(
      List<OpeningHourDto> hours, List<PriceDto> prices, List<ImageDto> images) {
    PlaceDetails details = placeDetails.normalize(hours, prices, images);
    int days = details.openingHours().size();
    if (days != 0 && days != WEEK) {
      throw new ApiException(HttpStatus.BAD_REQUEST, FULL_WEEK);
    }
    return details;
  }

  private void applyBasics(Place place, AdminPlaceRequest request) {
    PlaceCategory category = references.subtype(request.categoryCode());
    place.setName(request.name().trim());
    place.setCategory(category);
    place.setPlaceType(category.getCode());
    place.setProvince(references.province(request.provinceCode()));
    place.setAddress(request.address().trim());
    place.setDescription(blankToNull(request.description()));
    place.setLatitude(request.latitude());
    place.setLongitude(request.longitude());
    place.setPhone(blankToNull(request.phone()));
    place.setWebsite(blankToNull(request.website()));
  }

  private static void applyPrice(PlacePrice price, PriceDto request) {
    price.setPriceType(request.priceType().trim().toUpperCase(Locale.ROOT));
    price.setAmount(request.amount());
    price.setCurrency(request.currency() == null ? DEFAULT_CURRENCY : request.currency());
    price.setDescription(blankToNull(request.description()));
  }

  /** Photos, prices and hours live in other tables; bump the place so "last updated" follows. */
  private void touch(Place place) {
    place.setUpdatedAt(clock.instant());
    placeRepository.flush();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
