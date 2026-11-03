package com.dtrps.modules.businessowner.dto;

import java.time.Instant;

/**
 * A proposed change to an approved place. {@code status}: PENDING, APPROVED, REJECTED or CANCELLED
 * (withdrawn by the owner). {@code partnerFeedback} and {@code rejectReasonCode} are shown to the
 * owner; {@code reviewNote} is internal and only returned to admins.
 */
public record ListingRevisionResponse(
    Long id,
    Long listingId,
    String businessName,
    String status,
    ProposedPlace proposed,
    String rejectReasonCode,
    String partnerFeedback,
    String reviewNote,
    Instant createdAt,
    Instant reviewedAt) {}
