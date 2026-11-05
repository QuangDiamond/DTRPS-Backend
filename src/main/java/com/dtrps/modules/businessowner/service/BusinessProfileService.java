package com.dtrps.modules.businessowner.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.dto.BusinessProfileRequest;
import com.dtrps.modules.businessowner.dto.BusinessProfileResponse;
import com.dtrps.modules.businessowner.dto.BusinessProfileStatsResponse;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessProfileRepository;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.RoleRepository;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business profile lifecycle:
 *
 * <pre>
 * register ──► PENDING ──approve──► ACTIVE ◄──unlock── SUSPENDED
 *                 │                    └─────lock──────►┘
 *               reject
 *                 ▼
 *              INACTIVE ──owner edits and resubmits──► PENDING
 * </pre>
 *
 * Approval grants the BUSINESS_OWNER role. Roles travel in the access token, so the owner gets it
 * on their next token refresh or login.
 */
@Service
public class BusinessProfileService {
  public static final String PENDING = "PENDING";
  public static final String ACTIVE = "ACTIVE";
  public static final String SUSPENDED = "SUSPENDED";
  public static final String INACTIVE = "INACTIVE";

  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  static final String AUDIT_ENTITY = "BusinessProfile";
  static final String ACTION_SUBMITTED = "BUSINESS_PROFILE_SUBMITTED";
  static final String ACTION_UPDATED = "BUSINESS_PROFILE_UPDATED";
  static final String ACTION_RESUBMITTED = "BUSINESS_PROFILE_RESUBMITTED";
  static final String ACTION_APPROVED = "BUSINESS_PROFILE_APPROVED";
  static final String ACTION_REJECTED = "BUSINESS_PROFILE_REJECTED";
  static final String ACTION_SUSPENDED = "BUSINESS_PROFILE_SUSPENDED";
  static final String ACTION_REACTIVATED = "BUSINESS_PROFILE_REACTIVATED";

  private static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ doanh nghiệp";
  private static final String NO_PROFILE_YET = "Bạn chưa đăng ký hồ sơ doanh nghiệp";
  private static final String PROFILE_EXISTS = "Bạn đã có hồ sơ doanh nghiệp";
  private static final String TAX_CODE_TAKEN = "Mã số thuế đã được đăng ký bởi doanh nghiệp khác";
  private static final String PROFILE_LOCKED =
      "Hồ sơ doanh nghiệp đang bị khóa, vui lòng liên hệ quản trị viên";
  private static final String NOT_PENDING = "Chỉ có thể duyệt hoặc từ chối hồ sơ đang chờ duyệt";
  private static final String LOCK_NEEDS_ACTIVE = "Chỉ có thể khóa hồ sơ đang hoạt động";
  private static final String UNLOCK_NEEDS_SUSPENDED = "Chỉ có thể mở khóa hồ sơ đang bị khóa";
  private static final String LOCK_NEEDS_REASON = "Vui lòng nhập lý do khóa";
  private static final String CANNOT_REVIEW_OWN = "Không thể tự duyệt hồ sơ của chính mình";

  private final BusinessProfileRepository profileRepository;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final AuditService auditService;
  private final Clock clock;
  private final PartnerNotices notices;

  public BusinessProfileService(
      BusinessProfileRepository profileRepository,
      UserRepository userRepository,
      RoleRepository roleRepository,
      AuditService auditService,
      Clock clock,
      PartnerNotices notices) {
    this.profileRepository = profileRepository;
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.auditService = auditService;
    this.clock = clock;
    this.notices = notices;
  }

  // ===== Business owner =====

  @Transactional
  public BusinessProfileResponse register(Long userId, BusinessProfileRequest request) {
    if (profileRepository.existsByUserUserId(userId)) {
      throw new ApiException(HttpStatus.CONFLICT, PROFILE_EXISTS);
    }
    String taxCode = request.taxCode().trim();
    if (profileRepository.existsByTaxCode(taxCode)) {
      throw new ApiException(HttpStatus.CONFLICT, TAX_CODE_TAKEN);
    }
    User user = userRepository.getReferenceById(userId);
    BusinessProfile profile = new BusinessProfile();
    profile.setUser(user);
    apply(profile, request);
    profile.setStatus(PENDING);
    profileRepository.saveAndFlush(profile);
    auditService.record(userId, ACTION_SUBMITTED, AUDIT_ENTITY, profile.getBusinessId());
    notices.profileSubmitted(profile, false);
    return toResponse(profile);
  }

  @Transactional(readOnly = true)
  public BusinessProfileResponse getOwn(Long userId) {
    return toResponse(findOwn(userId));
  }

  /** Editing a rejected profile sends it back to the review queue. */
  @Transactional
  public BusinessProfileResponse updateOwn(Long userId, BusinessProfileRequest request) {
    BusinessProfile profile = findOwn(userId);
    if (SUSPENDED.equals(profile.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, PROFILE_LOCKED);
    }
    String taxCode = request.taxCode().trim();
    if (profileRepository.existsByTaxCodeAndBusinessIdNot(taxCode, profile.getBusinessId())) {
      throw new ApiException(HttpStatus.CONFLICT, TAX_CODE_TAKEN);
    }
    apply(profile, request);
    String action = ACTION_UPDATED;
    if (INACTIVE.equals(profile.getStatus())) {
      profile.setStatus(PENDING);
      action = ACTION_RESUBMITTED;
    }
    profileRepository.flush();
    auditService.record(userId, action, AUDIT_ENTITY, profile.getBusinessId());
    if (ACTION_RESUBMITTED.equals(action)) {
      notices.profileSubmitted(profile, true);
    } else {
      notices.profileUpdated(profile);
    }
    return toResponse(profile);
  }

  // ===== Admin =====

  /** {@code status} and {@code keyword} are optional; keyword matches name, tax code or emails. */
  @Transactional(readOnly = true)
  public PageResponse<BusinessProfileResponse> list(
      String status, String keyword, Pageable pageable) {
    Specification<BusinessProfile> spec = (root, query, cb) -> cb.conjunction();
    if (status != null && !status.isBlank()) {
      String wanted = status.trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) -> {
                Join<BusinessProfile, User> user = root.join("user");
                return cb.or(
                    cb.like(cb.lower(root.get("businessName")), like),
                    cb.like(cb.lower(root.get("taxCode")), like),
                    cb.like(cb.lower(root.get("contactEmail")), like),
                    cb.like(cb.lower(user.get("email")), like));
              });
    }
    return PageResponse.from(profileRepository.findAll(spec, pageable).map(this::toResponse));
  }

  /** "This month" follows Vietnam time, where the platform operates. */
  @Transactional(readOnly = true)
  public BusinessProfileStatsResponse stats() {
    Instant monthStart =
        LocalDate.now(clock.withZone(VIETNAM)).withDayOfMonth(1).atStartOfDay(VIETNAM).toInstant();
    return new BusinessProfileStatsResponse(
        profileRepository.count(),
        profileRepository.countByStatus(PENDING),
        profileRepository.countByStatus(ACTIVE),
        profileRepository.countByStatus(SUSPENDED),
        profileRepository.countByStatus(INACTIVE),
        profileRepository.countByCreatedAtGreaterThanEqual(monthStart));
  }

  @Transactional(readOnly = true)
  public BusinessProfileResponse get(Long businessId) {
    return toResponse(find(businessId));
  }

  @Transactional
  public BusinessProfileResponse approve(Long adminId, Long businessId) {
    BusinessProfile profile = findPendingForReview(adminId, businessId);
    profile.setStatus(ACTIVE);
    profile.setApprovedBy(userRepository.getReferenceById(adminId));
    profile.setApprovedAt(clock.instant());
    grantBusinessOwnerRole(profile.getUser());
    auditService.record(adminId, ACTION_APPROVED, AUDIT_ENTITY, businessId);
    notices.profileDecided(profile, adminId, "PARTNER_APPROVED", null);
    return toResponse(profile);
  }

  @Transactional
  public BusinessProfileResponse reject(Long adminId, Long businessId, String reason) {
    BusinessProfile profile = findPendingForReview(adminId, businessId);
    profile.setStatus(INACTIVE);
    String note = reason.trim();
    auditService.record(adminId, ACTION_REJECTED, AUDIT_ENTITY, businessId, note);
    notices.profileDecided(profile, adminId, "PARTNER_REJECTED", note);
    return BusinessProfileResponse.from(profile, note);
  }

  /** Locks (SUSPENDED) or unlocks (ACTIVE) an approved business. */
  @Transactional
  public BusinessProfileResponse updateStatus(
      Long adminId, Long businessId, String status, String reason) {
    BusinessProfile profile = find(businessId);
    if (SUSPENDED.equals(status)) {
      if (!ACTIVE.equals(profile.getStatus())) {
        throw new ApiException(HttpStatus.CONFLICT, LOCK_NEEDS_ACTIVE);
      }
      if (reason == null || reason.isBlank()) {
        throw new ApiException(HttpStatus.BAD_REQUEST, LOCK_NEEDS_REASON);
      }
      profile.setStatus(SUSPENDED);
      String note = reason.trim();
      auditService.record(adminId, ACTION_SUSPENDED, AUDIT_ENTITY, businessId, note);
      notices.profileDecided(profile, adminId, "PARTNER_SUSPENDED", note);
      return BusinessProfileResponse.from(profile, note);
    }
    if (!SUSPENDED.equals(profile.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, UNLOCK_NEEDS_SUSPENDED);
    }
    profile.setStatus(ACTIVE);
    auditService.record(adminId, ACTION_REACTIVATED, AUDIT_ENTITY, businessId);
    notices.profileDecided(profile, adminId, "PARTNER_REACTIVATED", null);
    return toResponse(profile);
  }

  // ===== Helpers =====

  private static void apply(BusinessProfile profile, BusinessProfileRequest request) {
    profile.setBusinessName(request.businessName().trim());
    profile.setTaxCode(request.taxCode().trim());
    profile.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
    profile.setContactPhone(request.contactPhone().trim());
  }

  private void grantBusinessOwnerRole(User user) {
    boolean hasRole =
        user.getRoles().stream()
            .anyMatch(role -> RoleName.BUSINESS_OWNER.name().equals(role.getRoleName()));
    if (hasRole) {
      return;
    }
    Role role =
        roleRepository
            .findByRoleName(RoleName.BUSINESS_OWNER.name())
            .orElseThrow(() -> new IllegalStateException("Missing role: BUSINESS_OWNER"));
    user.getRoles().add(role);
  }

  private BusinessProfile findPendingForReview(Long adminId, Long businessId) {
    BusinessProfile profile = find(businessId);
    if (profile.getUser().getUserId().equals(adminId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, CANNOT_REVIEW_OWN);
    }
    if (!PENDING.equals(profile.getStatus())) {
      throw new ApiException(HttpStatus.CONFLICT, NOT_PENDING);
    }
    return profile;
  }

  private BusinessProfile findOwn(Long userId) {
    return profileRepository
        .findByUserUserId(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NO_PROFILE_YET));
  }

  private BusinessProfile find(Long businessId) {
    return profileRepository
        .findWithUserByBusinessId(businessId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PROFILE_NOT_FOUND));
  }

  private BusinessProfileResponse toResponse(BusinessProfile profile) {
    String note =
        switch (profile.getStatus()) {
          case INACTIVE ->
              auditService
                  .latestDescription(AUDIT_ENTITY, profile.getBusinessId(), ACTION_REJECTED)
                  .orElse(null);
          case SUSPENDED ->
              auditService
                  .latestDescription(AUDIT_ENTITY, profile.getBusinessId(), ACTION_SUSPENDED)
                  .orElse(null);
          default -> null;
        };
    return BusinessProfileResponse.from(profile, note);
  }
}
