
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.TripDayProgress;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn tiến độ của từng ngày
 * trong một lần thực hiện lịch trình.
 */
public interface TripDayProgressRepository
        extends JpaRepository<TripDayProgress, Long> {

    // Lấy tiến độ của một ngày cụ thể trong chuyến đi.
    Optional<TripDayProgress> findByProgressProgressIdAndDayDayId(
            Long progressId,
            Long dayId
    );

    // Lấy danh sách ngày theo thứ tự Ngày 1, Ngày 2, ...
    List<TripDayProgress> findByProgressProgressIdOrderByDayDayNumberAsc(
            Long progressId
    );

    // Kiểm tra ngày đã có bản ghi tiến độ hay chưa.
    boolean existsByProgressProgressIdAndDayDayId(
            Long progressId,
            Long dayId
    );

    // Kiểm tra chuyến đi có ngày nào ở trạng thái chỉ định.
    boolean existsByProgressProgressIdAndStatus(
            Long progressId,
            String status
    );

    // Khóa bản ghi ngày đã tồn tại trước khi cập nhật.
    // Nếu ngày chưa có bản ghi, Service phải khóa TripProgress.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      SELECT dp
      FROM TripDayProgress dp
      WHERE dp.progress.progressId = :progressId
        AND dp.day.dayId = :dayId
      """)
    Optional<TripDayProgress> lockByProgressIdAndDayId(
            @Param("progressId") Long progressId,
            @Param("dayId") Long dayId
    );

    // Xóa tiến độ ngày trước khi xóa TripProgress
    // hoặc ItineraryDay khi xóa cả Travel Plan.
    // Phải được gọi trong transaction.
    @Modifying(flushAutomatically = true)
    @Query("""
      DELETE FROM TripDayProgress dp
      WHERE dp.progress.progressId IN (
        SELECT tp.progressId
        FROM TripProgress tp
        WHERE tp.itinerary.plan.planId = :planId
      )
      """)
    int deleteByPlanId(
            @Param("planId") Long planId
    );
}
