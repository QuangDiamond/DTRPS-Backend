package com.dtrps.modules.businessowner.dto;

import com.dtrps.modules.poi.dto.ImageDto;
import com.dtrps.modules.poi.dto.OpeningHourDto;
import com.dtrps.modules.poi.dto.PriceDto;
import com.dtrps.modules.poi.dto.ReferenceItem;
import java.util.List;

/** The full content a revision proposes for the place. */
public record ProposedPlace(
    String name,
    String description,
    String address,
    Double latitude,
    Double longitude,
    ReferenceItem group,
    ReferenceItem category,
    ReferenceItem province,
    String phone,
    String website,
    List<OpeningHourDto> openingHours,
    List<PriceDto> prices,
    List<ImageDto> images) {}
