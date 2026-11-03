-- Reference data. Runs on every startup and only adds rows that are missing.
-- Codes match the ids used by Admin Web and the mobile app.

-- Roles
INSERT INTO roles (role_name) VALUES
    ('TRAVELLER'),
    ('BUSINESS_OWNER'),
    ('ADMIN')
ON CONFLICT DO NOTHING;


-- 1. Provinces: the 34 provincial units in effect since 2025-07-01
INSERT INTO provinces (code, name) VALUES
    ('hanoi', 'Hà Nội'),
    ('hue', 'Huế'),
    ('haiphong', 'Hải Phòng'),
    ('danang', 'Đà Nẵng'),
    ('hcm', 'TP. Hồ Chí Minh'),
    ('cantho', 'Cần Thơ'),
    ('laichau', 'Lai Châu'),
    ('dienbien', 'Điện Biên'),
    ('sonla', 'Sơn La'),
    ('langson', 'Lạng Sơn'),
    ('quangninh', 'Quảng Ninh'),
    ('thanhhoa', 'Thanh Hóa'),
    ('nghean', 'Nghệ An'),
    ('hatinh', 'Hà Tĩnh'),
    ('caobang', 'Cao Bằng'),
    ('tuyenquang', 'Tuyên Quang'),
    ('laocai', 'Lào Cai'),
    ('thainguyen', 'Thái Nguyên'),
    ('phutho', 'Phú Thọ'),
    ('bacninh', 'Bắc Ninh'),
    ('hungyen', 'Hưng Yên'),
    ('ninhbinh', 'Ninh Bình'),
    ('quangtri', 'Quảng Trị'),
    ('quangngai', 'Quảng Ngãi'),
    ('gialai', 'Gia Lai'),
    ('khanhhoa', 'Khánh Hòa'),
    ('lamdong', 'Lâm Đồng'),
    ('daklak', 'Đắk Lắk'),
    ('dongnai', 'Đồng Nai'),
    ('tayninh', 'Tây Ninh'),
    ('vinhlong', 'Vĩnh Long'),
    ('dongthap', 'Đồng Tháp'),
    ('camau', 'Cà Mau'),
    ('angiang', 'An Giang')
ON CONFLICT DO NOTHING;


-- 2. Place Categories: 4 service groups, then their subtypes. Places reference a subtype.
INSERT INTO place_categories (code, name) VALUES
    ('lodging', 'Lưu trú'),
    ('fnb', 'Ẩm thực'),
    ('attraction', 'Vui chơi & Tham quan'),
    ('shopping', 'Mua sắm & Đặc sản')
ON CONFLICT DO NOTHING;

INSERT INTO place_categories (code, name, parent_id)
SELECT sub.code, sub.name, parent.category_id
FROM (VALUES
    ('hotel', 'Khách sạn', 'lodging'),
    ('resort', 'Resort', 'lodging'),
    ('homestay', 'Homestay', 'lodging'),
    ('villa', 'Villa', 'lodging'),
    ('restaurant', 'Nhà hàng', 'fnb'),
    ('cafe', 'Quán cà phê', 'fnb'),
    ('localEatery', 'Quán ăn địa phương', 'fnb'),
    ('barPub', 'Bar & Pub', 'fnb'),
    ('heritage', 'Di tích & Di sản', 'attraction'),
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


-- 3. Interests
INSERT INTO interests (code, name) VALUES
    ('nature', 'Thiên nhiên & Ngoài trời'),
    ('culture', 'Văn hóa & Lịch sử'),
    ('food', 'Ẩm thực'),
    ('photo', 'Chụp ảnh'),
    ('shopping', 'Mua sắm'),
    ('nightlife', 'Cuộc sống về đêm'),
    ('beach', 'Biển')
ON CONFLICT DO NOTHING;


-- 4. Transport Modes
INSERT INTO transport_modes (code, name) VALUES
    ('plane', 'Máy bay'),
    ('train', 'Tàu hỏa'),
    ('bus', 'Xe khách'),
    ('car', 'Ô tô'),
    ('motorbike', 'Xe máy'),
    ('walk', 'Đi bộ'),
    ('other', 'Khác')
ON CONFLICT DO NOTHING;


-- 5. Special Needs
INSERT INTO special_needs (code, name) VALUES
    ('vegetarian', 'Ăn chay'),
    ('diet', 'Hạn chế đồ ăn'),
    ('kids', 'Phù hợp trẻ em'),
    ('elderly', 'Phù hợp người lớn tuổi'),
    ('pets', 'Thân thiện với thú cưng'),
    ('other', 'Khác')
ON CONFLICT DO NOTHING;


-- 6. System Settings: defaults the admin can change later
INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('platform_name', 'Smart Travel Vietnam'),
    ('listing_sla_hours', '24'),
    ('session_timeout_minutes', '30')
ON CONFLICT DO NOTHING;
