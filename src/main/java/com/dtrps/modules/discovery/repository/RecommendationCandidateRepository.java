
package com.dtrps.modules.discovery.repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationCandidateRepository {

    private static final int DIMENSIONS = 1536;

    private final NamedParameterJdbcTemplate jdbc;

    public RecommendationCandidateRepository(
            NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SQL = """
        SELECT
            p.place_id,
            p.name,
            p.address,
            p.latitude,
            p.longitude,
            main_cat.code AS primary_category_code,

            ARRAY(
                SELECT x.code
                FROM (
                    SELECT c.code
                    FROM place_categories c
                    WHERE c.category_id = p.category_id

                    UNION

                    SELECT c.code
                    FROM place_category_links pcl
                    JOIN place_categories c
                      ON c.category_id = pcl.category_id
                    WHERE pcl.place_id = p.place_id
                ) x
                ORDER BY x.code
            ) AS category_codes,

            price.price_type,
            price.amount AS reference_price,

            %s AS cosine_similarity,
            e.embedding::text AS embedding_text

        FROM places p

        JOIN provinces province
          ON province.province_id = p.province_id

       
LEFT JOIN place_categories main_cat
  ON main_cat.category_id = p.category_id

LEFT JOIN place_categories main_parent
  ON main_parent.category_id = main_cat.parent_id


        %s place_embeddings e
          ON e.place_id = p.place_id

        LEFT JOIN LATERAL (
            SELECT pp.price_type, pp.amount
            FROM place_prices pp
            WHERE pp.place_id = p.place_id
              AND pp.currency = 'VND'
            ORDER BY pp.amount ASC, pp.price_id ASC
            LIMIT 1
        ) price ON TRUE

        WHERE p.status = 'ACTIVE'

          AND (
              LOWER(TRIM(province.name)) = :destination
              OR LOWER(TRIM(province.code)) = :destination
          )

          AND NOT EXISTS (
              SELECT 1
              FROM plan_places selected
              WHERE selected.place_id = p.place_id
                AND selected.plan_id = :planId
          )

          AND NOT EXISTS (
              SELECT 1
              FROM itinerary_items item
              JOIN itinerary_days day
                ON day.day_id = item.day_id
              JOIN itineraries itinerary
                ON itinerary.itinerary_id = day.itinerary_id
              WHERE itinerary.plan_id = :planId
                AND itinerary.status IN ('DRAFT', 'ACTIVE')
                AND item.place_id = p.place_id
                AND item.status NOT IN ('CANCELLED', 'SKIPPED')
          )

         
AND p.category_id IS NOT NULL

AND COALESCE(
    main_parent.code,
    main_cat.code
) <> 'lodging'


        ORDER BY %s
        """;

    public List<Candidate> findCandidates(
            Long planId,
            String destination,
            float[] queryVector) {

        return query(planId, destination, queryVector);
    }

    public List<Candidate> findRuleOnlyCandidates(
            Long planId,
            String destination) {

        return query(planId, destination, null);
    }

    private List<Candidate> query(
            Long planId,
            String destination,
            float[] queryVector) {

        if (planId == null
                || destination == null
                || destination.isBlank()) {
            throw new IllegalArgumentException(
                "PlanId và destination không hợp lệ"
            );
        }

        boolean hasVector = queryVector != null;

        String similarityExpression = hasVector
            ? "1.0 - (e.embedding <=> CAST(:queryVector AS vector))"
            : "NULL::double precision";

        String embeddingJoin = hasVector
            ? "JOIN"
            : "LEFT JOIN";

        String orderExpression = hasVector
            ? "e.embedding <=> CAST(:queryVector AS vector), p.place_id"
            : "p.place_id";

        String sql = BASE_SQL.formatted(
            similarityExpression,
            embeddingJoin,
            orderExpression
        );

        MapSqlParameterSource params =
            new MapSqlParameterSource()
                .addValue("planId", planId)
                .addValue(
                    "destination",
                    destination.trim().toLowerCase(Locale.ROOT)
                );

        if (hasVector) {
            params.addValue(
                "queryVector",
                toVectorLiteral(queryVector)
            );
        }

        return jdbc.query(sql, params, (rs, rowNum) ->
            mapCandidate(rs)
        );
    }

    private Candidate mapCandidate(ResultSet rs)
            throws SQLException {

        Array sqlArray = rs.getArray("category_codes");

        Set<String> categoryCodes = new LinkedHashSet<>();

        if (sqlArray != null) {
            String[] codes = (String[]) sqlArray.getArray();
            categoryCodes.addAll(Arrays.asList(codes));
        }

        return new Candidate(
            rs.getLong("place_id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("primary_category_code"),
            Collections.unmodifiableSet(categoryCodes),
            rs.getString("price_type"),
            rs.getBigDecimal("reference_price"),
            (Double) rs.getObject("latitude"),
            (Double) rs.getObject("longitude"),
            (Double) rs.getObject("cosine_similarity"),
            parseVector(rs.getString("embedding_text"))
        );
    }

    private String toVectorLiteral(float[] vector) {

        if (vector.length != DIMENSIONS) {
            throw new IllegalArgumentException(
                "Vector phải có 1536 chiều"
            );
        }

        StringBuilder result = new StringBuilder("[");

        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) {
                throw new IllegalArgumentException(
                    "Vector chứa giá trị không hợp lệ"
                );
            }

            if (i > 0) {
                result.append(",");
            }

            result.append(vector[i]);
        }

        return result.append("]").toString();
    }

    private float[] parseVector(String text) {

        if (text == null || text.isBlank()) {
            return null;
        }

        String content = text.substring(
            1, text.length() - 1
        );

        String[] parts = content.split(",");

        if (parts.length != DIMENSIONS) {
            throw new IllegalStateException(
                "Vector Place không đủ 1536 chiều"
            );
        }

        float[] result = new float[DIMENSIONS];

        for (int i = 0; i < DIMENSIONS; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }

        return result;
    }

    public record Candidate(
        Long placeId,
        String name,
        String address,
        String primaryCategoryCode,
        Set<String> categoryCodes,
        String priceType,
        BigDecimal referencePrice,
        Double latitude,
        Double longitude,
        Double cosineSimilarity,
        float[] placeVector
    ) {}
}
