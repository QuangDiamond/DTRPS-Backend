
package com.dtrps.modules.itinerary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tiến độ của một ngày trong một lần thực hiện chuyến đi.
 *
 * TripProgress đại diện cho toàn bộ Itinerary;
 * TripDayProgress đại diện cho một ItineraryDay thuộc Itinerary đó.
 *
 * Thời gian bắt đầu/kết thúc thực tế được lưu riêng,
 * không làm thay đổi lịch trình dự kiến.
 */
@Entity
@Table(
        name = "trip_day_progress",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_trip_day_progress_progress_day",
                        columnNames = {"progress_id", "day_id"})
        },
        indexes = {
                @Index(
                        name = "idx_trip_day_progress_day",
                        columnList = "day_id")
        })
@Check(
        name = "chk_trip_day_progress_status",
        constraints =
                "status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')")
@Getter
@Setter
public class TripDayProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "day_progress_id")
    private Long dayProgressId;

    /**
     * Tiến độ của toàn bộ chuyến đi.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "progress_id", nullable = false)
    private TripProgress progress;

    /**
     * Ngày trong lịch trình đang theo dõi.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    /**
     * NOT_STARTED: Chưa bắt đầu
     * IN_PROGRESS: Đang thực hiện
     * COMPLETED: Đã hoàn thành
     */
    @ColumnDefault("'NOT_STARTED'")
    @Column(
            name = "status",
            nullable = false,
            length = 30)
    private String status = "NOT_STARTED";

    /**
     * Thời điểm Traveller bắt đầu ngày.
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Thời điểm Traveller hoàn thành ngày.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(
            name = "updated_at",
            nullable = false)
    private Instant updatedAt;
}
