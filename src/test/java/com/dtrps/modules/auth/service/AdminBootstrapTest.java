package com.dtrps.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import com.dtrps.security.BootstrapAdminProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {
  private static final String EMAIL = "root@dtrps.test";
  private static final String PASSWORD = "Bootstrap123";

  @Mock private UserRepository userRepository;
  @Mock private UserService userService;

  @Test
  @DisplayName("does nothing without ADMIN_EMAIL")
  void run_withoutEmail_doesNothing() {
    bootstrap("", "").run(null);

    verify(userService, never()).createUser(any(), any(), any(), any());
  }

  @Test
  @DisplayName("fails fast when ADMIN_EMAIL is set without a password")
  void run_withoutPassword_fails() {
    AdminBootstrap bootstrap = bootstrap(EMAIL, "");

    assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("creates the admin once")
  void run_missingAdmin_createsIt() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

    bootstrap(EMAIL, PASSWORD).run(null);

    verify(userService).createUser("Root", EMAIL, PASSWORD, RoleName.ADMIN);
  }

  @Test
  @DisplayName("keeps an existing account untouched")
  void run_existingAdmin_doesNothing() {
    when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

    bootstrap(EMAIL, PASSWORD).run(null);

    verify(userService, never()).createUser(any(), any(), any(), any());
  }

  private AdminBootstrap bootstrap(String email, String password) {
    return new AdminBootstrap(
        new BootstrapAdminProperties(email, password, "Root"), userRepository, userService);
  }
}
