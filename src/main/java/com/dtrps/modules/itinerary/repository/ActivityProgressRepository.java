
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.ActivityProgress;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy xuất tiến độ của từng hoạt động (ItineraryItem) trong chuyến đi.
 *
 * Một cặp (progress_id, item_id) chỉ có một bản ghi ActivityProgress.
 */
public interface ActivityProgressRepository
        extends JpaRepository<ActivityProgress, Long> {

    // Lấy tiến độ một hoạt động thuộc chuyến đi hiện tại.
    Optional<ActivityProgress> findByProgressProgressIdAndItemItemId(
            Long progressId, Long itemId);

    // Kiểm tra hoạt động đã có bản ghi tiến độ trong chuyến đi hay chưa.
    boolean existsByProgressProgressIdAndItemItemId(
            Long progressId, Long itemId);

    // Danh sách tiến độ các hoạt động trong một ngày,
    // theo đúng thứ tự tham quan.
    @Query("""
      SELECT ap
      FROM ActivityProgress ap
      WHERE ap.progress.progressId = :progressId
        AND ap.item.day.dayId = :dayId
      ORDER BY ap.item.itemOrder ASC, ap.activityProgressId ASC
      """)
    List<ActivityProgress> findOrderedByProgressIdAndDayId(
            @Param("progressId") Long progressId,
            @Param("dayId") Long dayId);

    // Danh sách tiến độ tất cả hoạt động,
    // theo ngày rồi theo thứ tự tham quan.
    @Query("""
      SELECT ap
      FROM ActivityProgress ap
      WHERE ap.progress.progressId = :progressId
      ORDER BY ap.item.day.dayNumber ASC,
               ap.item.itemOrder ASC,
               ap.activityProgressId ASC
      """)
    List<ActivityProgress> findOrderedByProgressId(
            @Param("progressId") Long progressId);

    // Đếm số hoạt động trong ngày theo trạng thái.
    // PENDING, COMPLETED hoặc SKIPPED.
    @Query("""
      SELECT COUNT(ap)
      FROM ActivityProgress ap
      WHERE ap.progress.progressId = :progressId
        AND ap.item.day.dayId = :dayId
        AND ap.status = :status
      """)
    long countByProgressIdAndDayIdAndStatus(
            @Param("progressId") Long progressId,
            @Param("dayId") Long dayId,
            @Param("status") String status);

    // Khóa bản ghi khi cập nhật trạng thái hoạt động.
    // Phải gọi trong @Transactional.
    //
    // Nếu chưa có bản ghi, Service cần khóa TripProgress
    // trước khi kiểm tra và tạo ActivityProgress mới.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
      SELECT ap
      FROM ActivityProgress ap
      WHERE ap.progress.progressId = :progressId
        AND ap.item.itemId = :itemId
      """)
    Optional<ActivityProgress> lockByProgressIdAndItemId(
            @Param("progressId") Long progressId,
            @Param("itemId") Long itemId);
}
