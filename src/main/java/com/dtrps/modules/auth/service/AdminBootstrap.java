package com.dtrps.modules.auth.service;

import com.dtrps.modules.user.entity.RoleName;
import com.dtrps.modules.user.repository.UserRepository;
import com.dtrps.modules.user.service.UserService;
import com.dtrps.security.BootstrapAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Registration never grants ADMIN, so the first admin comes from environment variables. */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final BootstrapAdminProperties properties;
  private final UserRepository userRepository;
  private final UserService userService;

  public AdminBootstrap(
      BootstrapAdminProperties properties, UserRepository userRepository, UserService userService) {
    this.properties = properties;
    this.userRepository = userRepository;
    this.userService = userService;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (properties.adminEmail() == null || properties.adminEmail().isBlank()) {
      return;
    }
    if (properties.adminPassword() == null || properties.adminPassword().isBlank()) {
      throw new IllegalStateException("ADMIN_PASSWORD is required when ADMIN_EMAIL is set");
    }
    String email = UserService.normalizeEmail(properties.adminEmail());
    if (userRepository.existsByEmail(email)) {
      return;
    }
    userService.createUser(
        properties.adminName(), email, properties.adminPassword(), RoleName.ADMIN);
    log.info("Created bootstrap admin account");
  }
}
