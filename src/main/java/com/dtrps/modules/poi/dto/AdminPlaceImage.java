package com.dtrps.modules.poi.dto;

/** A place photo with its id, so the admin can delete it or make it the cover. */
public record AdminPlaceImage(Long id, String url, boolean primary) {}
