package com.dtrps.modules.user.service;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.service.TokenService;
import com.dtrps.modules.user.dto.UpdateProfileRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.RoleRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
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

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final TokenService tokenService;
  private final AuditService auditService;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      TokenService tokenService,
      AuditService auditService,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.passwordEncoder = passwordEncoder;
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(Long userId) {
    return UserResponse.from(findUser(userId));
  }

  @Transactional(readOnly = true)
  public PageResponse<UserResponse> list(Pageable pageable) {
    return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
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
    if (adminId.equals(userId)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, CANNOT_CHANGE_OWN_STATUS);
    }
    User user = findUser(userId);
    user.setStatus(status);
    if (!TokenService.USER_ACTIVE.equals(status)) {
      tokenService.revokeAll(userId);
    }
    auditService.record(adminId, "USER_STATUS_" + status, AUDIT_ENTITY, userId);
    return UserResponse.from(user);
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
