package com.dtrps.modules.businessowner.dto;

import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PriceDto;
import java.util.List;

/**
 * Stored as JSON in business_listing_revisions.payload: the proposed fields that have no column of
 * their own on the revision.
 */
public record RevisionPayload(
    String phone,
    String website,
    List<OpeningHourDto> openingHours,
    List<PriceDto> prices,
    List<ImageDto> images) {}
