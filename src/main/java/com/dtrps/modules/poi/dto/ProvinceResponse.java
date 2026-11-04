
package com.dtrps.modules.poi.dto;

/** Public province picker item: database id, stable code, and display name. */
public record ProvinceResponse(
        Long id,
        String code,
        String name
) {}
