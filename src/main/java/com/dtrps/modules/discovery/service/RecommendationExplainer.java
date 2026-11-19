package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.repository.RecommendationCandidateRepository.Candidate;
import com.dtrps.modules.discovery.service.PlaceScorer.ScoredCandidate;
import com.dtrps.modules.discovery.service.PlanProfileResolver.PlanProfile;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Giải thích vì sao một địa điểm được gợi ý: độ phù hợp (%) + các lý do + lưu ý.
 *
 * <p>Dựa trên luật, không gọi AI: lý do lấy từ chính các thành phần đã dùng để chấm điểm (khớp loại
 * hình, ngân sách, yêu cầu đặc biệt) và đối chiếu câu trả lời cá nhân hóa / hình thức đồng hành với mô
 * tả địa điểm – vốn được viết bằng cùng bộ từ vựng của form (hợp cặp đôi, yên tĩnh, thiên nhiên…).
 */
@Component
public class RecommendationExplainer {

    private static final int MAX_REASONS = 4;

    private record Hint(List<String> keywords, String reason) {}

    private static Hint hint(String reason, String... keywords) {
        return new Hint(List.of(keywords), reason);
    }

    /** Câu trả lời cá nhân hóa → từ khóa cần có trong mô tả + lý do hiển thị. */
    private static final Map<String, Hint> EXPERIENCE_HINTS = Map.ofEntries(
        Map.entry("PURPOSE_RELAX", hint("Hợp mong muốn thư giãn, chữa lành", "thư giãn", "nghỉ dưỡng", "thanh bình", "yên bình")),
        Map.entry("PURPOSE_ADVENTURE", hint("Có trải nghiệm phiêu lưu & khám phá", "phiêu lưu", "trekking", "leo núi", "mạo hiểm")),
        Map.entry("PURPOSE_BOND", hint("Dễ có hoạt động gắn kết người thân", "gia đình", "nhóm bạn", "cùng nhau")),
        Map.entry("PURPOSE_FUN", hint("Có hoạt động vui chơi & giải trí", "vui chơi", "giải trí", "trò chơi")),
        Map.entry("PURPOSE_CULTURE", hint("Giúp tìm hiểu văn hóa & lịch sử", "văn hóa", "lịch sử", "di tích", "bảo tàng")),
        Map.entry("PURPOSE_ROMANCE", hint("Không gian lãng mạn cho kỷ niệm đáng nhớ", "lãng mạn", "trăng mật", "hoàng hôn", "thơ mộng")),
        Map.entry("STYLE_QUIET", hint("Không gian yên tĩnh, nhẹ nhàng", "yên tĩnh", "tĩnh lặng", "ít khách", "ít đông", "yên bình")),
        Map.entry("STYLE_ACTIVE", hint("Không khí sôi động, nhiều hoạt động", "sôi động", "nhộn nhịp", "năng động")),
        Map.entry("STYLE_LOCAL", hint("Gần gũi đời sống địa phương", "địa phương", "người dân", "làng", "chợ")),
        Map.entry("STYLE_COMFORT", hint("Thoải mái, nhiều tiện nghi", "tiện nghi", "máy lạnh", "dịch vụ")),
        Map.entry("EXPERIENCE_NATURE", hint("Trải nghiệm thiên nhiên & sinh thái", "thiên nhiên", "sinh thái", "rừng")),
        Map.entry("EXPERIENCE_FOOD", hint("Có ẩm thực, đặc sản để thưởng thức", "ẩm thực", "đặc sản", "món")),
        Map.entry("EXPERIENCE_PHOTO", hint("Nhiều góc chụp ảnh, check-in đẹp", "chụp ảnh", "check-in", "góc ảnh")),
        Map.entry("EXPERIENCE_OUTDOOR", hint("Có hoạt động ngoài trời", "ngoài trời", "đạp xe", "chèo", "đi bộ")),
        Map.entry("EXPERIENCE_ART_CULTURE", hint("Có giá trị nghệ thuật, kiến trúc", "nghệ thuật", "kiến trúc", "chạm khắc", "làng nghề")),
        Map.entry("EXPERIENCE_WELLNESS", hint("Phù hợp nghỉ dưỡng & chăm sóc", "nghỉ dưỡng", "spa")),
        Map.entry("MEMORY_PHOTOS", hint("Nhiều góc chụp ảnh, check-in đẹp", "chụp ảnh", "check-in", "góc ảnh")),
        Map.entry("MEMORY_STORIES", hint("Gắn với câu chuyện, truyền thuyết thú vị", "truyền thuyết", "câu chuyện", "lịch sử")),
        Map.entry("MEMORY_UNFORGETTABLE", hint("Trải nghiệm độc đáo, khó quên", "độc đáo", "hiếm có", "nổi tiếng", "đặc biệt")),
        Map.entry("MEMORY_TOGETHER", hint("Dễ tạo kỷ niệm cùng mọi người", "gia đình", "nhóm bạn", "cùng nhau"))
    );

    private static final Map<String, Hint> SPECIAL_NEED_HINTS = Map.of(
        "kids", hint("Phù hợp với trẻ nhỏ", "trẻ nhỏ", "trẻ em"),
        "elderly", hint("Phù hợp người lớn tuổi", "người lớn tuổi"),
        "quietSpace", hint("Không gian yên tĩnh, ít đông", "yên tĩnh", "ít đông", "tĩnh lặng"),
        "largeGroup", hint("Rộng rãi, hợp nhóm đông người", "rộng", "nhóm bạn", "đoàn")
    );

    public record Explanation(int matchPercent, String matchLabel, List<String> reasons, List<String> cautions) {}

    public Explanation explain(ScoredCandidate scored, PlanProfile profile) {
        Candidate place = scored.candidate();
        String text = place.description() == null ? "" : place.description().toLowerCase(Locale.ROOT);
        Set<String> reasons = new LinkedHashSet<>();
        List<String> cautions = new ArrayList<>();

        // 1. Đúng loại hình người dùng thích.
        List<String> matched = new ArrayList<>();
        List<String> codes = profile.categoryCodes();
        List<String> names = profile.categoryNames();
        for (int i = 0; i < codes.size() && i < names.size(); i++) {
            String code = codes.get(i);
            if (place.categoryCodes().stream().anyMatch(c -> c.equalsIgnoreCase(code))) {
                matched.add(names.get(i));
            }
        }
        if (!matched.isEmpty()) {
            reasons.add("Đúng loại hình bạn thích: " + String.join(", ", matched));
        }

        // 2. Hợp hình thức đồng hành (mô tả có nhắc tới).
        String companionReason = companionReason(profile.companionType(), text);
        if (companionReason != null) {
            reasons.add(companionReason);
        }

        // 3. Hợp câu trả lời cá nhân hóa và yêu cầu đặc biệt.
        for (String code : profile.experienceCodes()) {
            addIfMentioned(reasons, EXPERIENCE_HINTS.get(code), text);
        }
        for (String need : profile.specialNeeds()) {
            addIfMentioned(reasons, SPECIAL_NEED_HINTS.get(need), text);
        }

        // 4. Chi phí so với ngân sách.
        String costReason = costReason(place, scored.breakdown().budgetScore(), cautions);
        if (costReason != null) {
            reasons.add(costReason);
        }

        // 5. Lưu ý theo yêu cầu đặc biệt.
        if (profile.limitedMobility() && place.strenuous()) {
            cautions.add("Có thể phải leo dốc hoặc đi bộ nhiều – cân nhắc nếu đi cùng người lớn tuổi hay người hạn chế vận động.");
        }

        // Không có lý do cụ thể: dựa vào độ gần ngữ nghĩa.
        Double semantic = scored.breakdown().semanticScore();
        if (reasons.isEmpty()) {
            reasons.add(semantic != null && semantic >= 0.6
                ? "Mô tả địa điểm rất gần với mong muốn của bạn"
                : "Điểm tham quan nổi bật tại " + profile.destination());
        }

        int percent = (int) Math.round(Math.max(0, Math.min(1, scored.score())) * 100);
        String label = percent >= 75 ? "Rất phù hợp" : percent >= 55 ? "Phù hợp" : "Có thể bạn thích";
        return new Explanation(percent, label, reasons.stream().limit(MAX_REASONS).toList(), List.copyOf(cautions));
    }

    private static void addIfMentioned(Set<String> reasons, Hint hint, String text) {
        if (hint != null && hint.keywords().stream().anyMatch(text::contains)) {
            reasons.add(hint.reason());
        }
    }

    private static String companionReason(String companion, String text) {
        if (companion == null) return null;
        String c = companion.trim().toLowerCase(Locale.ROOT);
        if (c.contains("cặp đôi") && text.contains("cặp đôi")) return "Phù hợp cho cặp đôi";
        if (c.contains("gia đình") && (text.contains("gia đình") || text.contains("trẻ nhỏ"))) return "Phù hợp cho gia đình có trẻ nhỏ";
        if (c.contains("nhóm bạn") && text.contains("nhóm bạn")) return "Phù hợp đi cùng nhóm bạn";
        if (c.contains("solo") && text.contains("solo")) return "Phù hợp khi đi một mình";
        return null;
    }

    private static String costReason(Candidate place, double budgetScore, List<String> cautions) {
        BigDecimal price = place.referencePrice();
        if (price == null || place.priceType() == null) return null;
        if (price.signum() == 0) return "Miễn phí tham quan";

        String unit = switch (place.priceType().toUpperCase(Locale.ROOT)) {
            case "ROOM_NIGHT" -> "/đêm";
            case "AVG_MEAL" -> "/người/bữa";
            case "AVG_DRINK" -> "/đồ uống";
            default -> "/người";
        };
        String amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(price) + " ₫" + unit;
        if (budgetScore >= 0.85) return "Chi phí thấp so với ngân sách của bạn (khoảng " + amount + ")";
        if (budgetScore < 0.5) cautions.add("Chi phí khá cao so với ngân sách mỗi ngày (khoảng " + amount + ").");
        return null;
    }
}
