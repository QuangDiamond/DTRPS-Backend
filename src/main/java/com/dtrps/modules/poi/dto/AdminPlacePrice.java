package com.dtrps.modules.poi.dto;

import java.math.BigDecimal;

/** A place price with its id, so the admin can edit or delete it. */
public record AdminPlacePrice(
    Long id, String priceType, BigDecimal amount, String currency, String description) {}
