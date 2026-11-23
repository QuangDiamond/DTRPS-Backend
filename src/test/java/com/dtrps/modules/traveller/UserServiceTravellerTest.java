
package com.dtrps.modules.traveller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.auth.service.TokenService;
import com.dtrps.modules.user.dto.UpdateProfileRequest;
import com.dtrps.modules.user.dto.UserResponse;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.RoleRepository;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTravellerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository,
                roleRepository,
                tokenService,
                auditService,
                passwordEncoder,
                Clock.systemUTC()
        );
    }

    // Tao user gia de test
    private User testUser() {
        User user = new User();
        user.setUserId(10L);
        user.setFullName("Nguyen Old");
        user.setEmail("test@gmail.com");
        return user;
    }

    // UTC001: Kiem tra chuan hoa email
    @Test
    @DisplayName("UTC001 - Normalize email")
    void utc001_normalizeEmail() {

        String result = UserService.normalizeEmail(
                " Test@Gmail.COM "
        );

        assertThat(result)
                .isEqualTo("test@gmail.com");
    }

    // UTC002: Lay thong tin user thanh cong
    @Test
    @DisplayName("UTC002 - Get user profile successfully")
    void utc002_getProfile_success() {

        User user = testUser();

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        UserResponse result = service.getProfile(10L);

        assertThat(result.id())
                .isEqualTo(10L);

        assertThat(result.email())
                .isEqualTo("test@gmail.com");

        verify(userRepository).findById(10L);
    }

    // UTC003: User khong ton tai
    @Test
    @DisplayName("UTC003 - User not found")
    void utc003_getProfile_notFound() {

        when(userRepository.findById(99999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.getProfile(99999L)
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.NOT_FOUND)
                );
    }

    // UTC004: Cap nhat thong tin user thanh cong
    @Test
    @DisplayName("UTC004 - Update profile successfully")
    void utc004_updateProfile_success() {

        User user = testUser();

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        UpdateProfileRequest request =
                new UpdateProfileRequest(
                        "Nguyen A",
                        "0912345678",
                        LocalDate.of(2000, 1, 1),
                        null
                );

        UserResponse result = service.updateProfile(
                10L,
                request
        );

        assertThat(result.name())
                .isEqualTo("Nguyen A");

        assertThat(result.phoneNumber())
                .isEqualTo("0912345678");

        assertThat(user.getFullName())
                .isEqualTo("Nguyen A");

        assertThat(user.getPhoneNumber())
                .isEqualTo("0912345678");

        verify(userRepository).findById(10L);
    }

    // UTC005: Cap nhat user khong ton tai
    @Test
    @DisplayName("UTC005 - Update profile user not found")
    void utc005_updateProfile_notFound() {

        when(userRepository.findById(99999L))
                .thenReturn(Optional.empty());

        UpdateProfileRequest request =
                new UpdateProfileRequest(
                        "Nguyen A",
                        "0912345678",
                        null,
                        null
                );

        assertThatThrownBy(
                () -> service.updateProfile(99999L, request)
        )
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(
                                ((ApiException) error).getStatus()
                        ).isEqualTo(HttpStatus.NOT_FOUND)
                );
    }

    // UTC006: Cap nhat avatar thanh cong
    @Test
    @DisplayName("UTC006 - Update avatar successfully")
    void utc006_updateAvatar_success() {

        User user = testUser();

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        String avatarUrl =
                "https://example.test/a.png";

        UserResponse result = service.updateAvatar(
                10L,
                avatarUrl
        );

        assertThat(result.avatarUrl())
                .isEqualTo(avatarUrl);

        assertThat(user.getAvatarUrl())
                .isEqualTo(avatarUrl);

        verify(userRepository).findById(10L);
    }
}
