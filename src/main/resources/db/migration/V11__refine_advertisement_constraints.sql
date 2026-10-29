-- V11: Refine Advertisement Constraints
-- Ensure advertisements belong to their listing owner

ALTER TABLE business_listings
ADD CONSTRAINT uq_business_listing_id_owner
UNIQUE (listing_id, business_id);

ALTER TABLE advertisements
ADD CONSTRAINT fk_advertisement_listing_owner
FOREIGN KEY (listing_id, business_id)
REFERENCES business_listings(listing_id, business_id);