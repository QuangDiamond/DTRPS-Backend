package com.dtrps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Starts on the schema an earlier context built, as a restart with DDL_AUTO=validate would. */
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidateTest extends IntegrationTestBase {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("a restart validates the schema and repeats startup scripts without duplicates")
  void restart_validatesSchemaAndKeepsReferenceData() {
    Integer roles = jdbcTemplate.queryForObject("SELECT count(*) FROM roles", Integer.class);

    assertThat(roles).isEqualTo(3);
  }
}
