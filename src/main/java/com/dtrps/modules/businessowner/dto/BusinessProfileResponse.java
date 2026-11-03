package com.dtrps.modules.businessowner.dto;

import com.dtrps.modules.businessowner.entity.BusinessProfile;
import java.time.Instant;

/**
 * {@code statusNote} is the reason an admin gave for the current INACTIVE (rejected) or SUSPENDED
 * status, and null otherwise.
 */
public record BusinessProfileResponse(
    Long id,
    Long userId,
    String ownerName,
    String ownerEmail,
    String businessName,
    String taxCode,
    String contactEmail,
    String contactPhone,
    String status,
    String statusNote,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt) {
  public static BusinessProfileResponse from(BusinessProfile profile, String statusNote) {
    return new BusinessProfileResponse(
        profile.getBusinessId(),
        profile.getUser().getUserId(),
        profile.getUser().getFullName(),
        profile.getUser().getEmail(),
        profile.getBusinessName(),
        profile.getTaxCode(),
        profile.getContactEmail(),
        profile.getContactPhone(),
        profile.getStatus(),
        statusNote,
        profile.getApprovedAt(),
        profile.getCreatedAt(),
        profile.getUpdatedAt());
  }
}
