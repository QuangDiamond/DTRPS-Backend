
package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.ItineraryItem;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryItemRepository
        extends JpaRepository<ItineraryItem, Long> {

    // Lấy hoạt động trong ngày theo đúng thứ tự
    @Query("""
      SELECT i
      FROM ItineraryItem i
      WHERE i.day.dayId = :dayId
      ORDER BY i.itemOrder ASC, i.itemId ASC
      """)
    List<ItineraryItem> findOrderedByDayId(
            @Param("dayId") Long dayId
    );

    // Lấy thứ tự lớn nhất trong một ngày
    @Query("""
      SELECT COALESCE(MAX(i.itemOrder), 0)
      FROM ItineraryItem i
      WHERE i.day.dayId = :dayId
      """)
    Integer findMaxOrderByDayId(
            @Param("dayId") Long dayId
    );

    // Cập nhật trực tiếp thứ tự trong transaction
    @Modifying
    @Query("""
      UPDATE ItineraryItem i
      SET i.itemOrder = :newOrder
      WHERE i.itemId = :itemId
      """)
    int updateOrder(
            @Param("itemId") Long itemId,
            @Param("newOrder") Integer newOrder
    );

    // Không xóa hoạt động đã có bản ghi theo dõi tiến độ
    @Query("""
      SELECT COUNT(p)
      FROM ActivityProgress p
      WHERE p.item.itemId = :itemId
      """)
    long countActivityProgressForItem(
            @Param("itemId") Long itemId
    );

    // Các địa điểm thuộc chuyến đi đang diễn ra hoặc bắt đầu trong khoảng [today, limit] — phạm
    // vi cập nhật của WeatherRefreshJob (chỉ lịch trình ACTIVE mới tính).
    @Query("""
      SELECT DISTINCT i.place.placeId
      FROM ItineraryItem i
      WHERE i.day.itinerary.status = 'ACTIVE'
        AND (i.day.itinerary.plan.status = 'IN_PROGRESS'
             OR i.day.itinerary.plan.startDate BETWEEN :today AND :limit)
      """)
    List<Long> findPlaceIdsForWeatherRefresh(
            @Param("today") LocalDate today,
            @Param("limit") LocalDate limit
    );
}
