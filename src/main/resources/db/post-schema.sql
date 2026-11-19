-- Objects entities cannot express. Runs on every startup, so each statement must be repeatable.
-- Statements end with @@ because the DO block contains semicolons.

-- Only one active subscription per business
CREATE UNIQUE INDEX IF NOT EXISTS uq_business_active_subscription
ON subscriptions(business_id)
WHERE status = 'ACTIVE'
@@

-- Only one primary image per place
CREATE UNIQUE INDEX IF NOT EXISTS uq_place_primary_image
ON place_images(place_id)
WHERE is_primary = TRUE
@@

-- Khách có thể đánh giá một địa điểm nhiều lần (mỗi lần thực sự đi lại được đánh giá thêm một
-- lần) nên bỏ unique cũ (ddl-auto=update không tự xóa constraint của những DB tạo trước đó)
ALTER TABLE place_reviews DROP CONSTRAINT IF EXISTS uq_place_review_user_place
@@

-- A business may own several places: drop the old one-listing-per-business unique constraint
-- (ddl-auto=update never removes constraints, so databases created earlier still have it)
DO $$
DECLARE
    old_constraint text;
BEGIN
    FOR old_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
        WHERE c.conrelid = 'business_listings'::regclass
          AND c.contype = 'u'
          AND array_length(c.conkey, 1) = 1
          AND a.attname = 'business_id'
    LOOP
        EXECUTE format('ALTER TABLE business_listings DROP CONSTRAINT %I', old_constraint);
    END LOOP;
END
$$
@@

-- Only one pending revision per listing
CREATE UNIQUE INDEX IF NOT EXISTS uq_listing_pending_revision
ON business_listing_revisions(listing_id)
WHERE status = 'PENDING'
@@

-- Only one pending revision per advertisement
CREATE UNIQUE INDEX IF NOT EXISTS uq_ad_pending_revision
ON advertisement_revisions(advertisement_id)
WHERE status = 'PENDING'
@@

-- Only one open window per subscription
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_open_window
ON subscription_renewal_windows(subscription_id)
WHERE status = 'OPEN'
@@

-- Vector search
CREATE INDEX IF NOT EXISTS idx_place_embeddings_vector
ON place_embeddings
USING hnsw (embedding vector_cosine_ops)
@@

-- A renewal payment must point at a window of its own subscription
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_renewal_window_subscription'
    ) THEN
        ALTER TABLE payments
        ADD CONSTRAINT fk_payment_renewal_window_subscription
            FOREIGN KEY (renewal_window_id, subscription_id)
            REFERENCES subscription_renewal_windows(window_id, subscription_id);
    END IF;
END
$$
@@

-- Dates and costs are derived, so they are read from here instead of being stored
CREATE OR REPLACE VIEW itinerary_day_summary AS
SELECT
    d.day_id,
    d.itinerary_id,
    d.day_number,
    (tp.start_date + (d.day_number - 1))::DATE AS visit_date,
    COALESCE(SUM(i.estimated_cost), 0) AS estimated_cost
FROM itinerary_days d
JOIN itineraries it ON it.itinerary_id = d.itinerary_id
JOIN travel_plans tp ON tp.plan_id = it.plan_id
LEFT JOIN itinerary_items i ON i.day_id = d.day_id
GROUP BY d.day_id, d.itinerary_id, d.day_number, tp.start_date
@@

-- Đánh dấu các loại hình ngoài trời cho tính năng cảnh báo thời tiết (UC-045). "beach" không có
-- trong place_categories (chỉ có ở bảng interests riêng) nên không đưa vào đây.
UPDATE place_categories
SET is_outdoor = TRUE
WHERE code IN
    ('park', 'scenicSpot', 'amusementPark', 'checkinSpot', 'craftVillage', 'nightMarket', 'heritage')
@@

-- WeatherForecast trước đây là 1 dòng/ngày (unique place_id+forecast_date); giờ đổi thành 1
-- dòng/giờ (unique place_id+forecast_date+forecast_time, xem entity). ddl-auto=update không tự
-- xóa constraint cũ nên phải xóa tay, nếu không insert dòng thứ 2 trong cùng 1 ngày sẽ vi phạm
-- unique cũ (lỗi "duplicate key value violates unique constraint uq_weather_place_date").
ALTER TABLE weather_forecasts DROP CONSTRAINT IF EXISTS uq_weather_place_date
@@

-- Dự báo giờ gộp thành dự báo ngày (GBR-47/49). MAX(weather_code) lấy mã lớn nhất trong ngày làm
-- "điều kiện xấu nhất của ngày" vì mã WMO lớn hơn nhìn chung tương ứng hiện tượng nặng hơn (giông
-- 95-99 > mưa 61-67 > mây 1-3 > quang 0) — đây là cách đơn giản hóa có chủ đích, không tuyệt đối
-- chính xác (vd. sương mù 45/48 nhẹ nhưng mã không phải nhỏ nhất); đủ dùng để hiển thị icon ngày.
CREATE OR REPLACE VIEW weather_daily_summary AS
SELECT
    place_id,
    forecast_date,
    MAX(temperature) AS temperature_max,
    MIN(temperature) AS temperature_min,
    MAX(precipitation_probability) AS precipitation_probability_max,
    COALESCE(SUM(precipitation), 0) AS precipitation_sum,
    MAX(wind_speed) AS wind_speed_max,
    MAX(weather_code) AS weather_code,
    MAX(fetched_at) AS fetched_at
FROM weather_forecasts
GROUP BY place_id, forecast_date
@@

CREATE OR REPLACE VIEW itinerary_cost_summary AS
SELECT
    it.itinerary_id,
    it.currency,
    COALESCE(SUM(i.estimated_cost), 0) AS total_estimated_cost
FROM itineraries it
LEFT JOIN itinerary_days d ON d.itinerary_id = it.itinerary_id
LEFT JOIN itinerary_items i ON i.day_id = d.day_id
GROUP BY it.itinerary_id, it.currency
@@

-- Every place lists its main type among its types (places saved before multi-type support, or
-- by older code, only had places.category_id)
INSERT INTO place_category_links (place_id, category_id)
SELECT place_id, category_id FROM places WHERE category_id IS NOT NULL
ON CONFLICT DO NOTHING
@@

-- The short-lived single "second type" column became place_category_links /
-- listing_revision_categories: move what it holds, then drop it
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'places' AND column_name = 'secondary_category_id') THEN
        INSERT INTO place_category_links (place_id, category_id)
        SELECT place_id, secondary_category_id FROM places WHERE secondary_category_id IS NOT NULL
        ON CONFLICT DO NOTHING;
        ALTER TABLE places DROP COLUMN secondary_category_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'business_listing_revisions'
                 AND column_name = 'secondary_category_id') THEN
        INSERT INTO listing_revision_categories (revision_id, category_id)
        SELECT revision_id, category_id FROM business_listing_revisions WHERE category_id IS NOT NULL
        UNION
        SELECT revision_id, secondary_category_id FROM business_listing_revisions
        WHERE secondary_category_id IS NOT NULL
        ON CONFLICT DO NOTHING;
        ALTER TABLE business_listing_revisions DROP COLUMN secondary_category_id;
    END IF;
END
$$
@@

-- Mã đơn PayOS: lấy từ DB để các máy cùng dùng DB chung không tạo trùng
CREATE SEQUENCE IF NOT EXISTS payment_order_code_seq START WITH 100000
@@

-- Mỗi đối tác chỉ có một đơn mua gói đang chờ thanh toán
CREATE UNIQUE INDEX IF NOT EXISTS uq_business_pending_subscription
ON subscriptions(business_id)
WHERE status = 'PENDING'
@@

-- Một địa điểm không chiếm hai lượt trong cùng một đăng ký
CREATE UNIQUE INDEX IF NOT EXISTS uq_ad_subscription_listing
ON advertisements(subscription_id, listing_id)
WHERE status IN ('PUBLISHED', 'SUSPENDED')
@@

-- Mỗi đơn thanh toán chỉ có một phiếu sự cố đang mở
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_issue_open
ON payment_issues(payment_id)
WHERE status = 'OPEN'
@@
