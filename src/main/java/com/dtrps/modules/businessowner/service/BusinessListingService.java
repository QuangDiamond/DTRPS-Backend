package com.dtrps.modules.businessowner.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.dto.BusinessListingRequest;
import com.dtrps.modules.businessowner.dto.BusinessListingResponse;
import com.dtrps.modules.businessowner.dto.BusinessListingStatsResponse;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessListingRepository;
import com.dtrps.modules.businessowner.repository.BusinessProfileRepository;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.service.PlaceDetailsService;
import com.dtrps.modules.poi.service.PlaceReferenceResolver;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A business's single place and its first approval:
 *
 * <pre>
 * submit ──► listing PENDING + place DRAFT ──approve──► listing APPROVED + place ACTIVE
 *                    │
 *                  reject (reason)
 *                    ▼
 *              listing REJECTED ──owner edits──► PENDING
 * </pre>
 *
 * Only an ACTIVE business profile may submit or edit. Until approved the owner edits the place
 * (including hours, prices and photos) directly, since nothing is public yet. Once approved, every
 * change goes through a listing revision ({@link ListingRevisionService}).
 */
@Service
public class BusinessListingService {
  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String SUSPENDED = "SUSPENDED";

  static final String PLACE_DRAFT = "DRAFT";
  static final String PLACE_ACTIVE = "ACTIVE";

  static final String AUDIT_ENTITY = "BusinessListing";
  static final String ACTION_SUBMITTED = "LISTING_SUBMITTED";
  static final String ACTION_UPDATED = "LISTING_UPDATED";
  static final String ACTION_RESUBMITTED = "LISTING_RESUBMITTED";
  static final String ACTION_APPROVED = "LISTING_APPROVED";
  static final String ACTION_REJECTED = "LISTING_REJECTED";

  private static final String PROFILE_NOT_ACTIVE =
      "Hồ sơ doanh nghiệp của bạn cần được duyệt trước khi đăng ký địa điểm";
  private static final String LISTING_NOT_FOUND = "Không tìm thấy địa điểm";
  private static final String NOT_EDITABLE =
      "Địa điểm đã được duyệt — vui lòng gửi đề xuất chỉnh sửa để Admin xét duyệt";
  private static final String NOT_PENDING = "Chỉ có thể duyệt hoặc từ chối địa điểm đang chờ duyệt";
  private static final String BUSINESS_NOT_ACTIVE =
      "Doanh nghiệp của địa điểm này đang không hoạt động";

  private final BusinessListingRepository listingRepository;
  private final BusinessProfileRepository profileRepository;
  private final PlaceRepository placeRepository;
  private final PlaceReferenceResolver references;
  private final PlaceDetailsService placeDetails;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final Clock clock;

  public BusinessListingService(
      BusinessListingRepository listingRepository,
      BusinessProfileRepository profileRepository,
      PlaceRepository placeRepository,
      PlaceReferenceResolver references,
      PlaceDetailsService placeDetails,
      UserRepository userRepository,
      AuditService auditService,
      Clock clock) {
    this.listingRepository = listingRepository;
    this.profileRepository = profileRepository;
    this.placeRepository = placeRepository;
    this.references = references;
    this.placeDetails = placeDetails;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.clock = clock;
  }

  // ===== Business owner =====

  @Transactional
  public BusinessListingResponse submit(Long userId, BusinessListingRequest request) {
    BusinessProfile business = activeBusinessOf(userId);
    PlaceDetails details = normalizedDetails(request);
    Place place = new Place();
    applyBasics(place, request);
    place.setStatus(PLACE_DRAFT);
    placeRepository.save(place);
    placeDetails.replace(place, details);

    BusinessListing listing = new BusinessListing();
    listing.setBusiness(business);
    listing.setPlace(place);
    listing.setStatus(PENDING);
    listingRepository.saveAndFlush(listing);
    auditService.record(userId, ACTION_SUBMITTED, AUDIT_ENTITY, listing.getListingId());
    return toResponse(listing);
  }

  /** Every place of the signed-in owner, newest first. */
  @Transactional(readOnly = true)
  public List<BusinessListingResponse> listOwn(Long userId) {
    return listingRepository.findByBusinessUserUserIdOrderByListingIdDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public BusinessListingResponse getOwn(Long userId, Long listingId) {
    return toResponse(findOwn(userId, listingId));
  }

  /** Editable while waiting or after a rejection; a rejected listing goes back to PENDING. */
  @Transactional
  public BusinessListingResponse updateOwn(
      Long userId, Long listingId, BusinessListingRequest request) {
    activeBusinessOf(userId);
    BusinessListing listing = findOwn(userId, listingId);
    if (!PENDING.equals(listing.getStatus()) && !REJECTED.equals(listing.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_EDITABLE);
    }
    PlaceDetails details = normalizedDetails(request);
    applyBasics(listing.getPlace(), request);
    placeDetails.replace(listing.getPlace(), details);
    String action = ACTION_UPDATED;
    if (REJECTED.equals(listing.getStatus())) {
      listing.setStatus(PENDING);
      action = ACTION_RESUBMITTED;
    }
    listingRepository.flush();
    auditService.record(userId, action, AUDIT_ENTITY, listing.getListingId());
    return toResponse(listing);
  }

  // ===== Admin =====

  /** Newest first. {@code keyword} matches the place name, business name or address. */
  @Transactional(readOnly = true)
  public PageResponse<BusinessListingResponse> list(
      String status, String keyword, Pageable pageable) {
    return list(status, keyword, null, pageable);
  }

  /** As above, optionally limited to one business ({@code businessId}). */
  @Transactional(readOnly = true)
  public PageResponse<BusinessListingResponse> list(
      String status, String keyword, Long businessId, Pageable pageable) {
    Specification<BusinessListing> spec = (root, query, cb) -> cb.conjunction();
    if (businessId != null) {
      spec =
          spec.and(
              (root, query, cb) -> cb.equal(root.get("business").get("businessId"), businessId));
    }
    if (status != null && !status.isBlank()) {
      String wanted = status.trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<BusinessListing, Place> place = root.join("place");
                Join<BusinessListing, BusinessProfile> business = root.join("business");
                return cb.or(
                    cb.like(cb.lower(place.get("name")), like),
                    cb.like(cb.lower(place.get("address")), like),
                    cb.like(cb.lower(business.get("businessName")), like));
              });
    }
    return PageResponse.from(listingRepository.findAll(spec, pageable).map(this::toResponse));
  }

  @Transactional(readOnly = true)
  public BusinessListingStatsResponse stats() {
    return new BusinessListingStatsResponse(
        listingRepository.count(),
        listingRepository.countByStatus(PENDING),
        listingRepository.countByStatus(APPROVED),
        listingRepository.countByStatus(REJECTED),
        listingRepository.countByStatus(SUSPENDED));
  }

  @Transactional(readOnly = true)
  public BusinessListingResponse get(Long listingId) {
    return toResponse(find(listingId));
  }

  /** Publishes the place. The business must still be ACTIVE. */
  @Transactional
  public BusinessListingResponse approve(Long adminId, Long listingId) {
    BusinessListing listing = findPending(listingId);
    requireActiveBusiness(listing);
    listing.setStatus(APPROVED);
    listing.setApprovedBy(userRepository.getReferenceById(adminId));
    listing.setApprovedAt(clock.instant());
    listing.getPlace().setStatus(PLACE_ACTIVE);
    auditService.record(adminId, ACTION_APPROVED, AUDIT_ENTITY, listingId);
    return toResponse(listing);
  }

  @Transactional
  public BusinessListingResponse reject(Long adminId, Long listingId, String reason) {
    BusinessListing listing = findPending(listingId);
    listing.setStatus(REJECTED);
    String note = reason.trim();
    auditService.record(adminId, ACTION_REJECTED, AUDIT_ENTITY, listingId, note);
    return BusinessListingResponse.from(
        listing, note, placeDetails.read(listing.getPlace().getPlaceId()));
  }

  // ===== Shared with ListingRevisionService =====

  BusinessProfile activeBusinessOf(Long userId) {
    return profileRepository
        .findByUserUserId(userId)
        .filter(profile -> BusinessProfileService.ACTIVE.equals(profile.getStatus()))
        .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, PROFILE_NOT_ACTIVE));
  }

  /** One of the owner's places; someone else's place looks the same as a missing one. */
  BusinessListing findOwn(Long userId, Long listingId) {
    return listingRepository
        .findWithDetailsByListingId(listingId)
        .filter(listing -> listing.getBusiness().getUser().getUserId().equals(userId))
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LISTING_NOT_FOUND));
  }

  void requireActiveBusiness(BusinessListing listing) {
    if (!BusinessProfileService.ACTIVE.equals(listing.getBusiness().getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, BUSINESS_NOT_ACTIVE);
    }
  }

  PlaceDetails normalizedDetails(BusinessListingRequest request) {
    return placeDetails.normalize(request.openingHours(), request.prices(), request.images());
  }

  BusinessListingResponse toResponse(BusinessListing listing) {
    String note =
        REJECTED.equals(listing.getStatus())
            ? auditService
                .latestDescription(AUDIT_ENTITY, listing.getListingId(), ACTION_REJECTED)
                .orElse(null)
            : null;
    return BusinessListingResponse.from(
        listing, note, placeDetails.read(listing.getPlace().getPlaceId()));
  }

  // ===== Helpers =====

  private void applyBasics(Place place, BusinessListingRequest request) {
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

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private BusinessListing find(Long listingId) {
    return listingRepository
        .findWithDetailsByListingId(listingId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, LISTING_NOT_FOUND));
  }

  private BusinessListing findPending(Long listingId) {
    BusinessListing listing = find(listingId);
    if (!PENDING.equals(listing.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_PENDING);
    }
    return listing;
  }
}
