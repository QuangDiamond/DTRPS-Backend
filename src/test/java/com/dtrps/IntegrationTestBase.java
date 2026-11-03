package com.dtrps;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Boots the real app on pgvector, so startup also proves entities build a working schema. */
@SpringBootTest(
    properties = {
      "DB_PASSWORD=provided-by-testcontainers",
      "JWT_SECRET=test-only-secret-test-only-secret-0123456789",
      "AUTH_RATE_LIMIT_PER_MINUTE=100000",
      "ADMIN_EMAIL=" + IntegrationTestBase.ADMIN_EMAIL,
      "ADMIN_PASSWORD=" + IntegrationTestBase.ADMIN_PASSWORD
    })
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {
  public static final String ADMIN_EMAIL = "admin@dtrps.test";
  public static final String ADMIN_PASSWORD = "TestAdmin123";

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

  static {
    POSTGRES.start();
  }
}
