
package com.dtrps.modules.itinerary.controller;

import com.dtrps.modules.itinerary.dto.ItineraryItemDtos;
import com.dtrps.modules.itinerary.service.ItineraryItemService;
import com.dtrps.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/itinerary-days/{dayId}/items")
public class ItineraryItemController {

    private final ItineraryItemService itemService;

    public ItineraryItemController(
            ItineraryItemService itemService
    ) {
        this.itemService = itemService;
    }

    // Lấy danh sách hoạt động của ngày
    @GetMapping
    public List<ItineraryItemDtos.ItemResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long dayId
    ) {
        return itemService.list(
                CurrentUser.id(jwt),
                dayId
        );
    }

    // Thêm hoạt động mới
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItineraryItemDtos.ItemResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long dayId,
            @Valid @RequestBody
            ItineraryItemDtos.CreateRequest request
    ) {
        return itemService.create(
                CurrentUser.id(jwt),
                dayId,
                request
        );
    }

    // Sửa hoạt động
    @PutMapping("/{itemId}")
    public ItineraryItemDtos.ItemResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long dayId,
            @PathVariable Long itemId,
            @Valid @RequestBody
            ItineraryItemDtos.UpdateRequest request
    ) {
        return itemService.update(
                CurrentUser.id(jwt),
                dayId,
                itemId,
                request
        );
    }

    // Xóa hoạt động
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long dayId,
            @PathVariable Long itemId
    ) {
        itemService.delete(
                CurrentUser.id(jwt),
                dayId,
                itemId
        );
    }

    // Sắp xếp lại các hoạt động trong ngày
    @PutMapping("/order")
    public List<ItineraryItemDtos.ItemResponse> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long dayId,
            @Valid @RequestBody
            ItineraryItemDtos.ReorderRequest request
    ) {
        return itemService.reorder(
                CurrentUser.id(jwt),
                dayId,
                request
        );
    }
}
