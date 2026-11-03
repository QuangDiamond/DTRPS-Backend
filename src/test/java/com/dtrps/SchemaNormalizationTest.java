package com.dtrps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaNormalizationTest extends IntegrationTestBase {
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("every foreign key is the leading part of an index")
  void foreignKeys_areAllIndexed() {
    List<String> unindexed =
        jdbcTemplate.queryForList(
            """
            SELECT c.conrelid::regclass || '.' || c.conname
            FROM pg_constraint c
            WHERE c.contype = 'f'
              AND c.connamespace = 'public'::regnamespace
              AND NOT EXISTS (
                SELECT 1 FROM pg_index i
                WHERE i.indrelid = c.conrelid
                  AND i.indpred IS NULL
                  AND (i.indkey::int2[])[0:cardinality(c.conkey) - 1] @> c.conkey
                  AND (i.indkey::int2[])[0] = ANY(c.conkey))
            """,
            String.class);

    assertThat(unindexed).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "provinces, 34",
    "place_categories, 21",
    "system_settings, 3",
    "interests, 7",
    "transport_modes, 7",
    "special_needs, 6",
    "roles, 3"
  })
  @DisplayName("reference data is seeded")
  void referenceData_isSeeded(String table, int expectedCount) {
    Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);

    assertThat(count).isEqualTo(expectedCount);
  }

  @Test
  @DisplayName("removed derived columns stay available through the summary views")
  void summaryViews_exist() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM itinerary_day_summary", Integer.class))
        .isNotNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM itinerary_cost_summary", Integer.class))
        .isNotNull();
  }

  @Test
  @DisplayName("an unknown place status is rejected by the database")
  void placeStatus_isConstrained() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO places (name, status) VALUES ('x', 'NOT_A_STATUS')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("a renewal payment must reference a renewal window")
  void renewalPayment_requiresWindow() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO payments (subscription_id, user_id, amount, payment_type)
                    VALUES (1, 1, 10, 'RENEWAL')
                    """))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
