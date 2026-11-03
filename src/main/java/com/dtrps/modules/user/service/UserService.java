package com.dtrps.modules.user.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.service.TokenService;
import com.dtrps.modules.user.dto.UpdateProfileRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.dto.UserStatsResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private static final String AUDIT_ENTITY = "User";
  private static final String USER_NOT_FOUND = "Không tìm thấy người dùng";
  private static final String CANNOT_CHANGE_OWN_STATUS =
      "Không thể thay đổi trạng thái của chính mình";
  private static final String SUSPENDED = "SUSPENDED";
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final TokenService tokenService;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public UserService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      TokenService tokenService,
      AuditService auditService,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(Long userId) {
    return UserResponse.from(findUser(userId));
  }

  /** Admin view of one account, with the lock reason while it is suspended. */
  @Transactional(readOnly = true)
  public UserResponse getForAdmin(Long userId) {
    User user = findUser(userId);
    return UserResponse.from(user, lockReason(user));
  }

  @Transactional(readOnly = true)
  public PageResponse<UserResponse> list(Pageable pageable) {
    return list(null, null, null, pageable);
  }

  /** Optional filters: keyword (name, email, phone), role name, status. */
  @Transactional(readOnly = true)
  public PageResponse<UserResponse> list(
      String keyword, String role, String status, Pageable pageable) {
    Specification<User> spec = (root, query, cb) -> cb.conjunction();
    if (keyword != null && !keyword.isBlank()) {
      String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("fullName")), like),
                      cb.like(cb.lower(root.get("email")), like),
                      cb.like(root.get("phoneNumber"), like)));
    }
    if (role != null && !role.isBlank()) {
      String wanted = role.trim().toUpperCase(Locale.ROOT);
      spec =
          spec.and(
              (root, query, cb) -> {
                query.distinct(true);
                Join<User, Role> roles = root.join("roles");
                return cb.equal(roles.get("roleName"), wanted);
              });
    }
    if (status != null && !status.isBlank()) {
      String wanted = status.trim().toUpperCase(Locale.ROOT);
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), wanted));
    }
    return PageResponse.from(
        userRepository
            .findAll(spec, pageable)
            .map(user -> UserResponse.from(user, lockReason(user))));
  }

  @Transactional(readOnly = true)
  public UserStatsResponse stats() {
    Instant monthStart =
        LocalDate.now(clock.withZone(VIETNAM)).withDayOfMonth(1).atStartOfDay(VIETNAM).toInstant();
    return new UserStatsResponse(
        userRepository.count(),
        userRepository.countByStatus(TokenService.USER_ACTIVE),
        userRepository.countByStatus(SUSPENDED),
        userRepository.countByRole(RoleName.TRAVELLER.name()),
        userRepository.countByRole(RoleName.BUSINESS_OWNER.name()),
        userRepository.countByRole(RoleName.ADMIN.name()),
        userRepository.countByCreatedAtGreaterThanEqual(monthStart));
  }

  private String lockReason(User user) {
    if (!SUSPENDED.equals(user.getStatus())) {
      return null;
    }
    return auditService
        .latestDescription(AUDIT_ENTITY, user.getUserId(), "USER_STATUS_" + SUSPENDED)
        .orElse(null);
  }

  @Transactional
  public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
    User user = findUser(userId);
    user.setFullName(request.name().trim());
    user.setPhoneNumber(request.phoneNumber());
    user.setDateOfBirth(request.dateOfBirth());
    user.setAvatarUrl(request.avatarUrl());
    return UserResponse.from(user);
  }

  /** A suspended user is cut off at the next refresh, once the access token expires. */
  @Transactional
  public UserResponse updateStatus(Long adminId, Long userId, String status) {
    return updateStatus(adminId, userId, status, null);
  }

  /** {@code reason} (optional) is kept in the audit log and shown to admins while locked. */
  @Transactional
  public UserResponse updateStatus(Long adminId, Long userId, String status, String reason) {
    if (adminId.equals(userId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, CANNOT_CHANGE_OWN_STATUS);
    }
    User user = findUser(userId);
    user.setStatus(status);
    if (!TokenService.USER_ACTIVE.equals(status)) {
      tokenService.revokeAll(userId);
    }
    String note = reason == null || reason.isBlank() ? null : reason.trim();
    auditService.record(adminId, "USER_STATUS_" + status, AUDIT_ENTITY, userId, note);
    return UserResponse.from(user, SUSPENDED.equals(status) ? note : null);
  }

  @Transactional
  public User createUser(String name, String email, String rawPassword, RoleName roleName) {
    Role role =
        roleRepository
            .findByRoleName(roleName.name())
            .orElseThrow(() -> new IllegalStateException("Missing role: " + roleName));
    User user = new User();
    user.setFullName(name.trim());
    user.setEmail(normalizeEmail(email));
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.getRoles().add(role);
    return userRepository.saveAndFlush(user);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, USER_NOT_FOUND));
  }
}
