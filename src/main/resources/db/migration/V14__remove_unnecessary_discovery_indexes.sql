
-- V14: Remove unnecessary indexes in Discovery module

DROP INDEX IF EXISTS idx_places_province;
DROP INDEX IF EXISTS idx_place_images_place;
DROP INDEX IF EXISTS idx_place_prices_place;
DROP INDEX IF EXISTS idx_place_reviews_user;
DROP INDEX IF EXISTS idx_favorites_place;
DROP INDEX IF EXISTS idx_route_from_place;
DROP INDEX IF EXISTS idx_route_to_place;
