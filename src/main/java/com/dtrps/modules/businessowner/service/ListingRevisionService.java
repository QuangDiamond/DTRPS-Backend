package com.dtrps.modules.businessowner.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.dto.BusinessListingRequest;
import com.dtrps.modules.businessowner.dto.ListingRevisionResponse;
import com.dtrps.modules.businessowner.dto.ProposedPlace;
import com.dtrps.modules.businessowner.dto.RejectRevisionRequest;
import com.dtrps.modules.businessowner.dto.RevisionPayload;
import com.dtrps.modules.businessowner.dto.RevisionReviewDetail;
import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessListingRevision;
import com.dtrps.modules.businessowner.repository.BusinessListingRevisionRepository;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.service.PlaceDetailsService;
import com.dtrps.modules.poi.service.PlaceReferenceResolver;
import com.dtrps.modules.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes to an approved place. The public place keeps its current content until an admin approves
 * the revision:
 *
 * <pre>
 * owner proposes ──► PENDING ──approve──► APPROVED (content copied to the place)
 *                       │  └──reject───► REJECTED (reason code + feedback for the owner)
 *                       └──owner withdraws──► CANCELLED
 * </pre>
 *
 * A listing has at most one PENDING revision (database index uq_listing_pending_revision); the
 * owner may propose again after it is approved, rejected or withdrawn.
 */
@Service
public class ListingRevisionService {
  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String REJECTED = "REJECTED";
  public static final String CANCELLED = "CANCELLED";

  static final String AUDIT_ENTITY = "BusinessListingRevision";
  static final String ACTION_PROPOSED = "REVISION_PROPOSED";
  static final String ACTION_CANCELLED = "REVISION_CANCELLED";
  static final String ACTION_APPROVED = "REVISION_APPROVED";
  static final String ACTION_REJECTED = "REVISION_REJECTED";

  private static final String NOT_APPROVED_YET =
      "Địa điểm chưa được duyệt — hãy sửa trực tiếp thông tin đăng ký";
  private static final String PENDING_EXISTS =
      "Đang có một đề xuất chỉnh sửa chờ duyệt — hãy rút lại trước khi gửi đề xuất mới";
  private static final String REVISION_NOT_FOUND = "Không tìm thấy đề xuất chỉnh sửa";
  private static final String NOT_PENDING = "Đề xuất này đã được xử lý";
  private static final String BROKEN_PAYLOAD = "Dữ liệu đề xuất bị lỗi";

  private final BusinessListingService listingService;
  private final BusinessListingRevisionRepository revisionRepository;
  private final PlaceReferenceResolver references;
  private final PlaceDetailsService placeDetails;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ListingRevisionService(
      BusinessListingService listingService,
      BusinessListingRevisionRepository revisionRepository,
      PlaceReferenceResolver references,
      PlaceDetailsService placeDetails,
      UserRepository userRepository,
      AuditService auditService,
      ObjectMapper objectMapper,
      Clock clock) {
    this.listingService = listingService;
    this.revisionRepository = revisionRepository;
    this.references = references;
    this.placeDetails = placeDetails;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  // ===== Business owner =====

  @Transactional
  public ListingRevisionResponse propose(
      Long userId, Long listingId, BusinessListingRequest request) {
    listingService.activeBusinessOf(userId);
    BusinessListing listing = listingService.findOwn(userId, listingId);
    if (!BusinessListingService.APPROVED.equals(listing.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_APPROVED_YET);
    }
    if (revisionRepository.existsByListingListingIdAndStatus(listing.getListingId(), PENDING)) {
      throw new ApiException(HttpStatus.CONFLICT, PENDING_EXISTS);
    }
    PlaceDetails details = listingService.normalizedDetails(request);

    BusinessListingRevision revision = new BusinessListingRevision();
    revision.setListing(listing);
    revision.setProposedBy(userRepository.getReferenceById(userId));
    revision.setCategory(references.subtype(request.categoryCode()));
    revision.setProvince(references.province(request.provinceCode()));
    revision.setName(request.name().trim());
    revision.setDescription(BusinessListingService.blankToNull(request.description()));
    revision.setAddress(request.address().trim());
    revision.setLatitude(request.latitude());
    revision.setLongitude(request.longitude());
    revision.setPayload(
        writePayload(
            new RevisionPayload(
                BusinessListingService.blankToNull(request.phone()),
                BusinessListingService.blankToNull(request.website()),
                details.openingHours(),
                details.prices(),
                details.images())));
    revision.setStatus(PENDING);
    revisionRepository.saveAndFlush(revision);
    auditService.record(userId, ACTION_PROPOSED, AUDIT_ENTITY, revision.getRevisionId());
    return toResponse(revision, false);
  }

  /** Revisions of one of the owner's places, newest first. */
  @Transactional(readOnly = true)
  public List<ListingRevisionResponse> listOwn(Long userId, Long listingId) {
    BusinessListing listing = listingService.findOwn(userId, listingId);
    return revisionRepository
        .findByListingListingIdOrderByRevisionIdDesc(listing.getListingId())
        .stream()
        .map(revision -> toResponse(revision, false))
        .toList();
  }

  /** Withdraws a revision that is still waiting, so a new one can be sent. */
  @Transactional
  public ListingRevisionResponse cancel(Long userId, Long revisionId) {
    BusinessListingRevision revision = find(revisionId);
    if (!revision.getListing().getBusiness().getUser().getUserId().equals(userId)) {
      // Someone else's revision looks the same as a missing one
      throw new ApiException(HttpStatus.NOT_FOUND, REVISION_NOT_FOUND);
    }
    requirePending(revision);
    revision.setStatus(CANCELLED);
    auditService.record(userId, ACTION_CANCELLED, AUDIT_ENTITY, revisionId);
    return toResponse(revision, false);
  }

  // ===== Admin =====

  /** Newest first. {@code keyword} matches the proposed or business name. */
  @Transactional(readOnly = true)
  public PageResponse<ListingRevisionResponse> list(
      String status, String keyword, Pageable pageable) {
    Specification<BusinessListingRevision> spec = (root, query, cb) -> cb.conjunction();
    if (status != null && !status.isBlank()) {
      String wanted = status.trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("name")), like),
                      cb.like(
                          cb.lower(root.join("listing").join("business").get("businessName")),
                          like)));
    }
    return PageResponse.from(
        revisionRepository.findAll(spec, pageable).map(revision -> toResponse(revision, true)));
  }

  /** The revision next to the place as it is public now. */
  @Transactional(readOnly = true)
  public RevisionReviewDetail get(Long revisionId) {
    BusinessListingRevision revision = find(revisionId);
    return new RevisionReviewDetail(
        toResponse(revision, true), listingService.toResponse(revision.getListing()));
  }

  /** Copies the proposed content to the public place. */
  @Transactional
  public RevisionReviewDetail approve(Long adminId, Long revisionId, String note) {
    BusinessListingRevision revision = find(revisionId);
    requirePending(revision);
    BusinessListing listing = revision.getListing();
    listingService.requireActiveBusiness(listing);
    RevisionPayload payload = readPayload(revision);

    Place place = listing.getPlace();
    place.setName(revision.getName());
    place.setDescription(revision.getDescription());
    place.setAddress(revision.getAddress());
    place.setLatitude(revision.getLatitude());
    place.setLongitude(revision.getLongitude());
    place.setCategory(revision.getCategory());
    place.setPlaceType(revision.getCategory().getCode());
    place.setProvince(revision.getProvince());
    place.setPhone(payload.phone());
    place.setWebsite(payload.website());
    placeDetails.replace(
        place, new PlaceDetails(payload.openingHours(), payload.prices(), payload.images()));

    markReviewed(revision, adminId, APPROVED);
    revision.setReviewNote(BusinessListingService.blankToNull(note));
    revisionRepository.flush();
    auditService.record(adminId, ACTION_APPROVED, AUDIT_ENTITY, revisionId);
    return new RevisionReviewDetail(toResponse(revision, true), listingService.toResponse(listing));
  }

  @Transactional
  public ListingRevisionResponse reject(
      Long adminId, Long revisionId, RejectRevisionRequest request) {
    BusinessListingRevision revision = find(revisionId);
    requirePending(revision);
    markReviewed(revision, adminId, REJECTED);
    revision.setRejectReasonCode(request.reasonCode());
    revision.setPartnerFeedback(request.partnerFeedback().trim());
    revision.setReviewNote(request.note().trim());
    auditService.record(
        adminId, ACTION_REJECTED, AUDIT_ENTITY, revisionId, request.partnerFeedback().trim());
    return toResponse(revision, true);
  }

  // ===== Helpers =====

  private void markReviewed(BusinessListingRevision revision, Long adminId, String status) {
    revision.setStatus(status);
    revision.setReviewedBy(userRepository.getReferenceById(adminId));
    revision.setReviewedAt(clock.instant());
  }

  private BusinessListingRevision find(Long revisionId) {
    return revisionRepository
        .findWithDetailsByRevisionId(revisionId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, REVISION_NOT_FOUND));
  }

  private static void requirePending(BusinessListingRevision revision) {
    if (!PENDING.equals(revision.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_PENDING);
    }
  }

  private String writePayload(RevisionPayload payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize revision payload", e);
    }
  }

  private RevisionPayload readPayload(BusinessListingRevision revision) {
    if (revision.getPayload() == null) {
      return new RevisionPayload(null, null, List.of(), List.of(), List.of());
    }
    try {
      RevisionPayload payload =
          objectMapper.readValue(revision.getPayload(), RevisionPayload.class);
      return new RevisionPayload(
          payload.phone(),
          payload.website(),
          payload.openingHours() == null ? List.of() : payload.openingHours(),
          payload.prices() == null ? List.of() : payload.prices(),
          payload.images() == null ? List.of() : payload.images());
    } catch (JsonProcessingException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, BROKEN_PAYLOAD);
    }
  }

  /** {@code forAdmin}: include the internal review note. */
  private ListingRevisionResponse toResponse(BusinessListingRevision revision, boolean forAdmin) {
    RevisionPayload payload = readPayload(revision);
    PlaceCategory category = revision.getCategory();
    PlaceCategory group = category == null ? null : category.getParent();
    ProposedPlace proposed =
        new ProposedPlace(
            revision.getName(),
            revision.getDescription(),
            revision.getAddress(),
            revision.getLatitude(),
            revision.getLongitude(),
            group == null ? null : new ReferenceItem(group.getCode(), group.getName()),
            category == null ? null : new ReferenceItem(category.getCode(), category.getName()),
            revision.getProvince() == null
                ? null
                : new ReferenceItem(
                    revision.getProvince().getCode(), revision.getProvince().getName()),
            payload.phone(),
            payload.website(),
            payload.openingHours(),
            payload.prices(),
            payload.images());
    return new ListingRevisionResponse(
        revision.getRevisionId(),
        revision.getListing().getListingId(),
        revision.getListing().getBusiness().getBusinessName(),
        revision.getStatus(),
        proposed,
        revision.getRejectReasonCode(),
        revision.getPartnerFeedback(),
        forAdmin ? revision.getReviewNote() : null,
        revision.getCreatedAt(),
        revision.getReviewedAt());
  }
}
