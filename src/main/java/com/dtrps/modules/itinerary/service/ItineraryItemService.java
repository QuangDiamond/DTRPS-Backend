
package com.dtrps.modules.itinerary.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.ItineraryItemDtos;
import com.dtrps.modules.itinerary.entity.ItineraryDay;
import com.dtrps.modules.itinerary.entity.ItineraryItem;
import com.dtrps.modules.itinerary.repository.ItineraryDayRepository;
import com.dtrps.modules.itinerary.repository.ItineraryItemRepository;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.TransportMode;
import com.dtrps.modules.poi.repository.PlaceRepository;
import com.dtrps.modules.poi.repository.TransportModeRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryItemService {

    private static final Set<String> VALID_STATUSES =
            Set.of("PLANNED", "COMPLETED", "SKIPPED", "CANCELLED");

    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final PlaceRepository placeRepository;
    private final TransportModeRepository transportRepository;
    private final EntityManager entityManager;

    public ItineraryItemService(
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            PlaceRepository placeRepository,
            TransportModeRepository transportRepository,
            EntityManager entityManager
    ) {
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.placeRepository = placeRepository;
        this.transportRepository = transportRepository;
        this.entityManager = entityManager;
    }

    // Lấy danh sách hoạt động của một ngày
    @Transactional(readOnly = true)
    public List<ItineraryItemDtos.ItemResponse> list(
            Long userId,
            Long dayId
    ) {
        requireDay(userId, dayId, false);
        return loadResponses(dayId);
    }

    // Thêm hoạt động mới vào cuối ngày
    @Transactional
    public ItineraryItemDtos.ItemResponse create(
            Long userId,
            Long dayId,
            ItineraryItemDtos.CreateRequest request
    ) {
        ItineraryDay day = requireDay(userId, dayId, true);

        validateTimes(request.startTime(), request.endTime());

        Integer maxOrder =
                itemRepository.findMaxOrderByDayId(dayId);

        if (maxOrder == Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể thêm hoạt động vì thứ tự đã đạt giới hạn"
            );
        }

        ItineraryItem item = new ItineraryItem();

        item.setDay(day);
        item.setPlace(requireActivePlace(request.placeId()));
        item.setItemOrder(maxOrder + 1);
        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setEstimatedCost(request.estimatedCost());
        item.setTransportMode(findTransport(request.transportModeId()));
        item.setTravelTimeMinutes(request.travelTimeMinutes());
        item.setNotes(request.notes());
        item.setStatus("PLANNED");

        return toResponse(itemRepository.saveAndFlush(item));
    }

    // Sửa hoạt động, không thay đổi thứ tự ở endpoint này
    @Transactional
    public ItineraryItemDtos.ItemResponse update(
            Long userId,
            Long dayId,
            Long itemId,
            ItineraryItemDtos.UpdateRequest request
    ) {
        requireDay(userId, dayId, true);

        ItineraryItem item = requireItem(dayId, itemId);

        validateTimes(request.startTime(), request.endTime());

        if (request.placeId() != null) {
            item.setPlace(requireActivePlace(request.placeId()));
        }

        item.setStartTime(request.startTime());
        item.setEndTime(request.endTime());
        item.setEstimatedCost(request.estimatedCost());
        item.setTransportMode(findTransport(request.transportModeId()));
        item.setTravelTimeMinutes(request.travelTimeMinutes());
        item.setNotes(request.notes());

        if (request.status() != null) {
            if (!VALID_STATUSES.contains(request.status())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Trạng thái hoạt động không hợp lệ"
                );
            }
            item.setStatus(request.status());
        }

        return toResponse(itemRepository.saveAndFlush(item));
    }

    // Xóa hoạt động và đánh số lại các hoạt động còn lại
    @Transactional
    public void delete(
            Long userId,
            Long dayId,
            Long itemId
    ) {
        requireDay(userId, dayId, true);

        ItineraryItem item = requireItem(dayId, itemId);

        if (itemRepository.countActivityProgressForItem(itemId) > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Hoạt động đã có tiến độ theo dõi, không thể xóa"
            );
        }

        itemRepository.delete(item);
        itemRepository.flush();

        List<ItineraryItem> remaining =
                itemRepository.findOrderedByDayId(dayId);

        List<Long> orderedIds = remaining.stream()
                .map(ItineraryItem::getItemId)
                .toList();

        rewriteOrders(dayId, orderedIds, remaining);
    }

    // Sắp xếp hoạt động theo danh sách itemId gửi từ Frontend
    @Transactional
    public List<ItineraryItemDtos.ItemResponse> reorder(
            Long userId,
            Long dayId,
            ItineraryItemDtos.ReorderRequest request
    ) {
        requireDay(userId, dayId, true);

        List<ItineraryItem> existing =
                itemRepository.findOrderedByDayId(dayId);

        List<Long> requestedIds = request.itemIds();

        if (requestedIds == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách thứ tự không được để trống"
            );
        }

        List<Long> existingIds = existing.stream()
                .map(ItineraryItem::getItemId)
                .toList();

        Set<Long> requestedSet = new HashSet<>(requestedIds);
        Set<Long> existingSet = new HashSet<>(existingIds);

        if (
                requestedIds.size() != existingIds.size() ||
                        requestedSet.size() != requestedIds.size() ||
                        !requestedSet.equals(existingSet)
        ) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Danh sách hoạt động phải chứa đủ ID thuộc ngày, không trùng lặp"
            );
        }

        rewriteOrders(dayId, requestedIds, existing);

        return loadResponses(dayId);
    }

    /**
     * Sắp xếp hai giai đoạn để tránh trùng UNIQUE(day_id,item_order).
     * Giai đoạn 1: chuyển tất cả hoạt động sang số thứ tự tạm.
     * Giai đoạn 2: gán thứ tự chính thức từ 1 đến N.
     * Cả hai giai đoạn chạy trong cùng một transaction.
     */
    private void rewriteOrders(
            Long dayId,
            List<Long> orderedIds,
            List<ItineraryItem> currentItems
    ) {
        if (orderedIds.isEmpty()) {
            return;
        }

        int maxOrder = currentItems.stream()
                .mapToInt(ItineraryItem::getItemOrder)
                .max()
                .orElse(0);

        if ((long) maxOrder + orderedIds.size() > Integer.MAX_VALUE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Không thể sắp xếp vì giá trị thứ tự đã đạt giới hạn"
            );
        }

        int temporaryStart = maxOrder + 1;

        // Giai đoạn 1: sử dụng vị trí tạm chưa có ai chiếm
        for (int index = 0; index < orderedIds.size(); index++) {
            int updated = itemRepository.updateOrder(
                    orderedIds.get(index),
                    temporaryStart + index
            );

            if (updated != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Danh sách hoạt động đã thay đổi, vui lòng thử lại"
                );
            }
        }

        // Giai đoạn 2: đưa về thứ tự liên tiếp 1, 2, 3...
        for (int index = 0; index < orderedIds.size(); index++) {
            int updated = itemRepository.updateOrder(
                    orderedIds.get(index),
                    index + 1
            );

            if (updated != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Không thể cập nhật thứ tự hoạt động"
                );
            }
        }

        // Bulk update không đồng bộ entity đã nạp vào bộ nhớ.
        // Clear để lần đọc tiếp theo lấy lại thứ tự mới từ database.
        entityManager.clear();
    }

    // Kiểm tra quyền sở hữu và trạng thái lịch trình
    private ItineraryDay requireDay(
            Long userId,
            Long dayId,
            boolean forEditing
    ) {
        ItineraryDay day = (
                forEditing
                        ? dayRepository.lockByDayId(dayId)
                        : dayRepository.findById(dayId)
        ).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy ngày trong lịch trình"
        ));

        Long ownerId = day.getItinerary()
                .getPlan()
                .getUser()
                .getUserId();

        if (!ownerId.equals(userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy ngày trong lịch trình"
            );
        }

        if (
                forEditing &&
                        !"ACTIVE".equals(day.getItinerary().getStatus())
        ) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Chỉ được chỉnh sửa lịch trình ACTIVE"
            );
        }

        return day;
    }

    // Kiểm tra hoạt động có thuộc đúng ngày hay không
    private ItineraryItem requireItem(
            Long dayId,
            Long itemId
    ) {
        ItineraryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy hoạt động"
                ));

        if (!item.getDay().getDayId().equals(dayId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy hoạt động trong ngày này"
            );
        }

        return item;
    }

    // Chỉ cho phép thêm địa điểm đang ACTIVE
    private Place requireActivePlace(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy địa điểm"
                ));

        if (!"ACTIVE".equals(place.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Địa điểm chưa được kích hoạt"
            );
        }

        return place;
    }

    // Phương tiện là tùy chọn
    private TransportMode findTransport(Long transportModeId) {
        if (transportModeId == null) {
            return null;
        }

        return transportRepository.findById(transportModeId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy phương tiện di chuyển"
                ));
    }

    // Kiểm tra thời gian theo ràng buộc Backend
    private void validateTimes(
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (
                startTime != null &&
                        endTime != null &&
                        !endTime.isAfter(startTime)
        ) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }
    }

    // Chuyển entity thành response
    private ItineraryItemDtos.ItemResponse toResponse(
            ItineraryItem item
    ) {
        TransportMode mode = item.getTransportMode();

        return new ItineraryItemDtos.ItemResponse(
                item.getItemId(),
                item.getDay().getDayId(),
                item.getItemOrder(),
                item.getPlace().getPlaceId(),
                item.getPlace().getName(),
                item.getStartTime(),
                item.getEndTime(),
                item.getEstimatedCost(),
                mode == null ? null : mode.getTransportModeId(),
                mode == null ? null : mode.getName(),
                item.getTravelTimeMinutes(),
                item.getNotes(),
                item.getStatus()
        );
    }

    private List<ItineraryItemDtos.ItemResponse> loadResponses(
            Long dayId
    ) {
        List<ItineraryItemDtos.ItemResponse> result =
                new ArrayList<>();

        for (ItineraryItem item :
                itemRepository.findOrderedByDayId(dayId)) {
            result.add(toResponse(item));
        }

        return result;
    }
}
