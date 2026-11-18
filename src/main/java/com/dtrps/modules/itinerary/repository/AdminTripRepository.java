package com.dtrps.modules.itinerary.repository;

import com.dtrps.modules.itinerary.entity.Itinerary;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.entity.PlanPlace;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.entity.TripProgress;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn chỉ đọc cho trang Quản lý chuyến đi: gom theo trang để tránh N+1. */
public interface AdminTripRepository
    extends JpaRepository<TravelPlan, Long>, JpaSpecificationExecutor<TravelPlan> {

  @Override
  @EntityGraph(attributePaths = "user")
  Page<TravelPlan> findAll(Specification<TravelPlan> spec, Pageable pageable);

  /** [status, số chuyến] */
  @Query("select p.status, count(p) from TravelPlan p group by p.status")
  List<Object[]> countGroupedByStatus();

  @Query("select count(p) from TravelPlan p where p.status = 'IN_PROGRESS' and p.endDate < :today")
  long countOverdue(@Param("today") LocalDate today);

  @Query("select distinct p.destination from TravelPlan p order by p.destination")
  List<String> destinations();

  @Query("select i from Itinerary i where i.plan.planId in :planIds")
  List<Itinerary> itinerariesOf(@Param("planIds") Collection<Long> planIds);

  /** [itineraryId, số ngày] */
  @Query(
      "select d.itinerary.itineraryId, count(d) from ItineraryDay d"
          + " where d.itinerary.itineraryId in :itineraryIds group by d.itinerary.itineraryId")
  List<Object[]> dayCounts(@Param("itineraryIds") Collection<Long> itineraryIds);

  /** [itineraryId, số điểm đến] */
  @Query(
      "select i.day.itinerary.itineraryId, count(i) from ItineraryItem i"
          + " where i.day.itinerary.itineraryId in :itineraryIds"
          + " group by i.day.itinerary.itineraryId")
  List<Object[]> stopCounts(@Param("itineraryIds") Collection<Long> itineraryIds);

  @Query("select p from TripProgress p where p.itinerary.itineraryId in :itineraryIds")
  List<TripProgress> progressOf(@Param("itineraryIds") Collection<Long> itineraryIds);

  /** [progressId, số thứ tự ngày, trạng thái ngày] */
  @Query(
      "select d.progress.progressId, d.day.dayNumber, d.status from TripDayProgress d"
          + " where d.progress.progressId in :progressIds")
  List<Object[]> dayStates(@Param("progressIds") Collection<Long> progressIds);

  /** [progressId, trạng thái điểm đến, số lượng] */
  @Query(
      "select a.progress.progressId, a.status, count(a) from ActivityProgress a"
          + " where a.progress.progressId in :progressIds"
          + " group by a.progress.progressId, a.status")
  List<Object[]> activityCounts(@Param("progressIds") Collection<Long> progressIds);

  @Query(
      "select i from ItineraryItem i join fetch i.place"
          + " where i.day.itinerary.itineraryId = :itineraryId"
          + " order by i.day.dayNumber asc, i.itemOrder asc, i.itemId asc")
  List<ItineraryItem> stopsOf(@Param("itineraryId") Long itineraryId);

  @Query(
      "select p from PlanPlace p join fetch p.place where p.plan.planId = :planId"
          + " order by p.planPlaceId asc")
  List<PlanPlace> selectedPlaces(@Param("planId") Long planId);
}
