
-- V11: Refine advertisement constraints
-- Allow multiple advertisements per business listing

-- Ensure each advertisement belongs to its listing owner
ALTER TABLE business_listings
ADD CONSTRAINT uq_business_listing_id_owner
UNIQUE (listing_id, business_id);

ALTER TABLE advertisements
ADD CONSTRAINT fk_advertisement_listing_owner
FOREIGN KEY (listing_id, business_id)
REFERENCES business_listings(listing_id, business_id);

-- All advertisements are displayed on Homepage
ALTER TABLE advertisements
ADD COLUMN placement VARCHAR(30)
NOT NULL DEFAULT 'HOMEPAGE';

ALTER TABLE advertisements
ADD CONSTRAINT chk_advertisement_placement
CHECK (placement = 'HOMEPAGE');
