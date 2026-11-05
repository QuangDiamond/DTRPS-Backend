
package com.dtrps.modules.itinerary.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ItinerarySummaryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ItinerarySummaryRepository(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Dữ liệu trả về từ itinerary_day_summary
    public record DaySummary(
            Long dayId,
            Integer dayNumber,
            LocalDate visitDate,
            BigDecimal estimatedCost
    ) {}

    // Dữ liệu trả về từ itinerary_cost_summary
    public record CostSummary(
            String currency,
            BigDecimal totalEstimatedCost
    ) {}

    /**
     * TASK 47
     * Lấy ngày đi và tổng chi phí từng ngày.
     *
     * Đọc từ view itinerary_day_summary.
     */
    public List<DaySummary> findDaySummaries(
            Long itineraryId
    ) {

        String sql = """
        SELECT
            day_id,
            day_number,
            visit_date,
            estimated_cost
        FROM itinerary_day_summary
        WHERE itinerary_id = ?
        ORDER BY day_number ASC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DaySummary(
                        rs.getLong("day_id"),
                        rs.getInt("day_number"),
                        rs.getObject("visit_date", LocalDate.class),
                        rs.getBigDecimal("estimated_cost")
                ),
                itineraryId
        );
    }

    /**
     * TASK 47
     * Lấy tổng chi phí toàn bộ lịch trình.
     *
     * Đọc từ view itinerary_cost_summary.
     */
    public Optional<CostSummary> findCostSummary(
            Long itineraryId
    ) {

        String sql = """
        SELECT
            currency,
            total_estimated_cost
        FROM itinerary_cost_summary
        WHERE itinerary_id = ?
        """;

        List<CostSummary> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CostSummary(
                        rs.getString("currency"),
                        rs.getBigDecimal("total_estimated_cost")
                ),
                itineraryId
        );

        return results.stream().findFirst();
    }
}
