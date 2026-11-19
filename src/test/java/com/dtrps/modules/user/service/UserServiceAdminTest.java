package com.dtrps.modules.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.service.TokenService;
import com.dtrps.modules.user.controller.AdminUserController;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.RoleRepository;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test UserService (phần admin) — sheet User, UTC001–UTC006 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class UserServiceAdminTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Pageable NEWEST_FIRST =
      PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "userId"));

  @Mock private UserRepository users;
  @Mock private RoleRepository roles;
  @Mock private TokenService tokenService;
  @Mock private AuditService auditService;
  @Mock private PasswordEncoder passwordEncoder;

  private UserService service;
  private User locked;

  @BeforeEach
  void setUp() {
    service =
        new UserService(
            users,
            roles,
            tokenService,
            auditService,
            passwordEncoder,
            Clock.fixed(NOW, ZoneOffset.UTC));
    locked = new User();
    locked.setUserId(5L);
    locked.setFullName("Nguyễn Văn A");
    locked.setEmail("a@gmail.com");
    locked.setStatus("SUSPENDED");
    lenient().when(users.findById(5L)).thenReturn(Optional.of(locked));
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("getForAdmin")
  class GetForAdmin {
    @Test
    @DisplayName("UTC001 Locked user → details with the lock reason from the audit log")
    void locked_withReason() {
      when(auditService.latestDescription("User", 5L, "USER_STATUS_SUSPENDED"))
          .thenReturn(Optional.of("Spam đánh giá"));

      UserResponse response = service.getForAdmin(5L);

      assertThat(response.status()).isEqualTo("SUSPENDED");
      assertThat(response.statusNote()).isEqualTo("Spam đánh giá");
    }

    @Test
    @DisplayName("UTC002 Unknown user → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.getForAdmin(999_999L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy người dùng");
    }
  }

  @Nested
  @DisplayName("list")
  class ListUsers {
    @Test
    @DisplayName("UTC003 Filters → matching users mapped, newest-first paging kept")
    void filters_mapped() {
      User active = new User();
      active.setUserId(9L);
      active.setFullName("Nguyễn Thị B");
      active.setStatus("ACTIVE");
      when(users.findAll(any(Specification.class), any(Pageable.class)))
          .thenReturn(new PageImpl<>(List.of(active), NEWEST_FIRST, 1));

      PageResponse<UserResponse> page = service.list("nguyen", "traveller", "ACTIVE", NEWEST_FIRST);

      verify(users)
          .findAll(any(Specification.class), org.mockito.ArgumentMatchers.eq(NEWEST_FIRST));
      assertThat(page.items()).extracting(UserResponse::id).containsExactly(9L);
      assertThat(page.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTC004 size=101 → 400 (validation on AdminUserController.list); 100 accepted")
    void sizeOverMax_invalid() throws Exception {
      ExecutableValidator validator =
          Validation.buildDefaultValidatorFactory().getValidator().forExecutables();
      Method list =
          AdminUserController.class.getMethod(
              "list", String.class, String.class, String.class, int.class, int.class);
      AdminUserController controller = mock(AdminUserController.class);

      Set<String> templates =
          validator
              .validateParameters(controller, list, new Object[] {null, null, null, 0, 101})
              .stream()
              .map(ConstraintViolation::getMessageTemplate)
              .collect(Collectors.toSet());

      assertThat(templates).containsExactly("{jakarta.validation.constraints.Max.message}");
      assertThat(
              validator.validateParameters(
                  controller, list, new Object[] {null, null, null, 0, 100}))
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("updateAvatar")
  class UpdateAvatar {
    @Test
    @DisplayName("UTC005 Existing user → new avatar saved")
    void existing_saved() {
      String url = "https://res.cloudinary.com/f3wrjuzb/image/upload/dtrps/avatars/a.jpg";

      UserResponse response = service.updateAvatar(5L, url);

      assertThat(locked.getAvatarUrl()).isEqualTo(url);
      assertThat(response.avatarUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("UTC006 Deleted user → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.updateAvatar(999_999L, "https://x/a.jpg"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy người dùng");
    }
  }
}
