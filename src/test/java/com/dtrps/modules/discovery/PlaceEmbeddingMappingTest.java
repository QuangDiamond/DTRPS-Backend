package com.dtrps.modules.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.dtrps.IntegrationTestBase;
import com.dtrps.modules.discovery.entity.PlaceEmbedding;
import com.dtrps.modules.discovery.entity.SearchLog;
import com.dtrps.modules.discovery.repository.PlaceEmbeddingRepository;
import com.dtrps.modules.discovery.repository.SearchLogRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.repository.PlaceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PlaceEmbeddingMappingTest extends IntegrationTestBase {
  private static final int DIMENSIONS = 1536;

  @Autowired private PlaceRepository placeRepository;
  @Autowired private PlaceEmbeddingRepository placeEmbeddingRepository;
  @Autowired private SearchLogRepository searchLogRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("vector columns round-trip and are searchable by cosine distance")
  void embedding_roundTripsAndIsSearchable() {
    Place beach = savePlace("Beach");
    Place museum = savePlace("Museum");
    saveEmbedding(beach, 0);
    saveEmbedding(museum, 1);
    placeEmbeddingRepository.flush();

    List<Long> ranked =
        jdbcTemplate.queryForList(
            "SELECT place_id FROM place_embeddings ORDER BY embedding <=> ?::vector LIMIT 2",
            Long.class,
            vectorLiteral(0));

    assertThat(ranked).containsExactly(beach.getPlaceId(), museum.getPlaceId());
  }

  @Test
  @DisplayName("jsonb columns round-trip as JSON")
  void searchLogFilters_storedAsJsonb() {
    SearchLog log = new SearchLog();
    log.setSearchQuery("biển đẹp");
    log.setFilters("{\"city\":\"Da Nang\"}");
    SearchLog saved = searchLogRepository.saveAndFlush(log);

    String city =
        jdbcTemplate.queryForObject(
            "SELECT filters->>'city' FROM search_logs WHERE search_id = ?",
            String.class,
            saved.getSearchId());

    assertThat(city).isEqualTo("Da Nang");
  }

  private Place savePlace(String name) {
    Place place = new Place();
    place.setName(name);
    return placeRepository.save(place);
  }

  private void saveEmbedding(Place place, int hotIndex) {
    PlaceEmbedding embedding = new PlaceEmbedding();
    embedding.setPlace(place);
    embedding.setEmbedding(oneHot(hotIndex));
    embedding.setSourceText("test");
    placeEmbeddingRepository.save(embedding);
  }

  private static float[] oneHot(int hotIndex) {
    float[] vector = new float[DIMENSIONS];
    vector[hotIndex] = 1f;
    return vector;
  }

  private static String vectorLiteral(int hotIndex) {
    StringBuilder literal = new StringBuilder("[");
    for (int i = 0; i < DIMENSIONS; i++) {
      literal.append(i == 0 ? "" : ",").append(i == hotIndex ? "1" : "0");
    }
    return literal.append(']').toString();
  }
}
