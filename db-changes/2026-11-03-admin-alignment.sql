-- One-off change for databases created before 2026-11-03 (e.g. the shared server DB).
-- Hibernate (ddl-auto=update) adds the new columns and tables by itself but does not change existing
-- CHECK constraints or clean up old categories, so run this ONCE, AFTER starting the new code once.
-- Fresh databases (docker compose down -v) do not need it.
--
--   psql -h <host> -p 5433 -U dtrps -d dtrps -f db-changes/2026-11-03-admin-alignment.sql

BEGIN;

-- 1. Listing revisions: business owners can withdraw (CANCELLED); reject reasons limited to 5 codes
ALTER TABLE business_listing_revisions DROP CONSTRAINT IF EXISTS chk_listing_revision_status;
ALTER TABLE business_listing_revisions ADD CONSTRAINT chk_listing_revision_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

ALTER TABLE business_listing_revisions DROP CONSTRAINT IF EXISTS chk_listing_revision_reject_reason;
ALTER TABLE business_listing_revisions ADD CONSTRAINT chk_listing_revision_reject_reason
    CHECK (reject_reason_code IS NULL
        OR reject_reason_code IN ('pricing', 'location', 'photos', 'duplicate', 'other'));

-- 2. Categories: replace the flat list with 4 groups + 17 subtypes
-- Old rows whose names clash with the new groups ('Lưu trú', 'Ẩm thực') get a temporary name
UPDATE place_categories SET name = name || ' (old)'
WHERE code IN ('nature', 'food', 'stay', 'craft', 'tour') AND parent_id IS NULL;

INSERT INTO place_categories (code, name) VALUES
    ('lodging', 'Lưu trú'),
    ('fnb', 'Ẩm thực'),
    ('attraction', 'Vui chơi & Tham quan'),
    ('shopping', 'Mua sắm & Đặc sản')
ON CONFLICT DO NOTHING;

-- heritage and resort already exist: turn them into subtypes
UPDATE place_categories c
SET parent_id = p.category_id, name = v.name
FROM (VALUES
    ('heritage', 'Di tích & Di sản', 'attraction'),
    ('resort', 'Resort', 'lodging')
) AS v(code, name, parent_code)
JOIN place_categories p ON p.code = v.parent_code
WHERE c.code = v.code;

INSERT INTO place_categories (code, name, parent_id)
SELECT sub.code, sub.name, parent.category_id
FROM (VALUES
    ('hotel', 'Khách sạn', 'lodging'),
    ('homestay', 'Homestay', 'lodging'),
    ('villa', 'Villa', 'lodging'),
    ('restaurant', 'Nhà hàng', 'fnb'),
    ('cafe', 'Quán cà phê', 'fnb'),
    ('localEatery', 'Quán ăn địa phương', 'fnb'),
    ('barPub', 'Bar & Pub', 'fnb'),
    ('amusementPark', 'Khu vui chơi', 'attraction'),
    ('park', 'Công viên', 'attraction'),
    ('scenicSpot', 'Danh lam thắng cảnh', 'attraction'),
    ('checkinSpot', 'Điểm check-in', 'attraction'),
    ('nightMarket', 'Chợ đêm', 'shopping'),
    ('specialtyStore', 'Cửa hàng đặc sản', 'shopping'),
    ('souvenirShop', 'Cửa hàng lưu niệm', 'shopping'),
    ('craftVillage', 'Làng nghề', 'shopping')
) AS sub(code, name, parent_code)
JOIN place_categories parent ON parent.code = sub.parent_code
ON CONFLICT DO NOTHING;

-- Move existing places and revisions off the old categories.
-- 'tour' has no equivalent: those rows lose their category and must be re-categorised by an admin.
CREATE TEMP TABLE category_remap (old_code text, new_code text) ON COMMIT DROP;
INSERT INTO category_remap VALUES
    ('nature', 'scenicSpot'),
    ('food', 'localEatery'),
    ('stay', 'hotel'),
    ('craft', 'craftVillage'),
    ('tour', NULL);

UPDATE places pl
SET category_id = nc.category_id
FROM category_remap m
JOIN place_categories oc ON oc.code = m.old_code
LEFT JOIN place_categories nc ON nc.code = m.new_code
WHERE pl.category_id = oc.category_id;

UPDATE business_listing_revisions r
SET category_id = nc.category_id
FROM category_remap m
JOIN place_categories oc ON oc.code = m.old_code
LEFT JOIN place_categories nc ON nc.code = m.new_code
WHERE r.category_id = oc.category_id;

DELETE FROM place_categories WHERE code IN ('nature', 'food', 'stay', 'craft', 'tour');

COMMIT;
