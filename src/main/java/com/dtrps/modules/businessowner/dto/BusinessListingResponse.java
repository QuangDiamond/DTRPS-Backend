package com.dtrps.modules.businessowner.dto;

import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PlaceDetails;
import com.dtrps.modules.poi.dto.PlaceTypes;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.dto.ReferenceItem;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import java.time.Instant;
import java.util.List;

/**
 * A business's place and its review state. {@code status} is the listing status (PENDING, APPROVED,
 * REJECTED, SUSPENDED); {@code placeStatus} is the place's visibility status. {@code statusNote}
 * holds the admin's reason while the listing is REJECTED.
 */
public record BusinessListingResponse(
    Long id,
    Long businessId,
    String businessName,
    Long placeId,
    String name,
    String description,
    String address,
    Double latitude,
    Double longitude,
    String phone,
    String website,
    List<OpeningHourDto> openingHours,
    List<PriceDto> prices,
    List<ImageDto> images,
    ReferenceItem group,
    ReferenceItem category,
    /** Every group / type of the place, the main one first */
    List<ReferenceItem> groups,
    List<PlaceTypes.Type> categories,
    ReferenceItem province,
    String placeStatus,
    String status,
    String statusNote,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt) {
  public static BusinessListingResponse from(
      BusinessListing listing, String statusNote, PlaceDetails details) {
    Place place = listing.getPlace();
    PlaceCategory category = place.getCategory();
    PlaceCategory group = category == null ? null : category.getParent();
    PlaceTypes types = PlaceTypes.of(category, place.getCategories());
    return new BusinessListingResponse(
        listing.getListingId(),
        listing.getBusiness().getBusinessId(),
        listing.getBusiness().getBusinessName(),
        place.getPlaceId(),
        place.getName(),
        place.getDescription(),
        place.getAddress(),
        place.getLatitude(),
        place.getLongitude(),
        place.getPhone(),
        place.getWebsite(),
        details.openingHours(),
        details.prices(),
        details.images(),
        group == null ? null : new ReferenceItem(group.getCode(), group.getName()),
        category == null ? null : new ReferenceItem(category.getCode(), category.getName()),
        types.groups(),
        types.categories(),
        place.getProvince() == null
            ? null
            : new ReferenceItem(place.getProvince().getCode(), place.getProvince().getName()),
        place.getStatus(),
        listing.getStatus(),
        statusNote,
        listing.getApprovedAt(),
        listing.getCreatedAt(),
        listing.getUpdatedAt());
  }
}
