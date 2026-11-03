package com.dtrps.modules.poi.dto;

import java.util.List;

/** One of the 4 service groups with its active subtypes. Places reference a subtype code. */
public record CategoryGroupResponse(String code, String name, List<ReferenceItem> subtypes) {}
