package com.dtrps.modules.businessowner.dto;

/** What the admin compares: the proposed content next to what is public now. */
public record RevisionReviewDetail(
    ListingRevisionResponse revision, BusinessListingResponse current) {}
