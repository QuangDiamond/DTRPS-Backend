
package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.service
    .PlanProfileResolver.PlanProfile;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Dựng câu mô tả nhu cầu để nhúng (embedding) và so với vector địa điểm.
 *
 * <p>Các cụm từ được chọn khớp với bộ từ vựng đã dùng khi viết mô tả địa điểm (hình thức đồng hành,
 * ngân sách, nhịp độ, loại trải nghiệm…), nên phần ngữ nghĩa phản ánh được câu trả lời cá nhân hóa
 * và yêu cầu đặc biệt mà không cần suy diễn chúng thành loại hình địa điểm.
 */
@Component
public class PreferenceQueryBuilder {

    /** Câu trả lời cá nhân hóa (TravelPlan.experiencePreferences) → cụm từ mô tả. */
    private static final Map<String, String> EXPERIENCE_PHRASES = Map.ofEntries(
        Map.entry("PURPOSE_RELAX", "thư giãn, chữa lành, nghỉ dưỡng & spa, không gian thanh bình"),
        Map.entry("PURPOSE_ADVENTURE", "phiêu lưu & khám phá, leo núi, trekking, trải nghiệm mới lạ"),
        Map.entry("PURPOSE_BOND", "gắn kết người thân, hoạt động chung cho gia đình và nhóm bạn"),
        Map.entry("PURPOSE_FUN", "vui chơi & giải trí, khu vui chơi, hoạt động sôi động"),
        Map.entry("PURPOSE_CULTURE", "tìm hiểu văn hóa & lịch sử, di tích, bảo tàng, đền chùa"),
        Map.entry("PURPOSE_ROMANCE", "kỷ niệm lãng mạn cho cặp đôi, ngắm hoàng hôn, khung cảnh thơ mộng"),
        Map.entry("STYLE_QUIET", "yên tĩnh, nhẹ nhàng, ít đông khách"),
        Map.entry("STYLE_ACTIVE", "năng động, sôi nổi, nhiều hoạt động"),
        Map.entry("STYLE_FREE", "tự do, ngẫu hứng, dạo phố"),
        Map.entry("STYLE_PLANNED", "điểm tham quan tiêu biểu, nổi tiếng"),
        Map.entry("STYLE_COMFORT", "thoải mái, tiện nghi, dịch vụ chu đáo"),
        Map.entry("STYLE_LOCAL", "gần gũi đời sống địa phương, chợ, làng nghề, ẩm thực đường phố"),
        Map.entry("EXPERIENCE_NATURE", "thiên nhiên & sinh thái, cảnh đẹp, rừng, biển, hồ"),
        Map.entry("EXPERIENCE_FOOD", "thưởng thức ẩm thực, món ăn đặc sản"),
        Map.entry("EXPERIENCE_PHOTO", "chụp ảnh, check-in, góc ảnh đẹp"),
        Map.entry("EXPERIENCE_OUTDOOR", "hoạt động ngoài trời"),
        Map.entry("EXPERIENCE_ART_CULTURE", "nghệ thuật & văn hóa, kiến trúc cổ, làng nghề"),
        Map.entry("EXPERIENCE_WELLNESS", "nghỉ dưỡng & spa, chăm sóc sức khỏe"),
        Map.entry("MEMORY_PHOTOS", "những bức ảnh đẹp, điểm check-in"),
        Map.entry("MEMORY_STORIES", "câu chuyện lịch sử, kiến thức, truyền thuyết"),
        Map.entry("MEMORY_UNFORGETTABLE", "trải nghiệm độc đáo, khó quên"),
        Map.entry("MEMORY_TOGETHER", "kỷ niệm cùng mọi người, hoạt động chung")
    );

    /** Yêu cầu đặc biệt (Preference.specialNeeds) → cụm từ mô tả. Thú cưng chưa có dữ liệu địa điểm. */
    private static final Map<String, String> SPECIAL_NEED_PHRASES = Map.of(
        "kids", "phù hợp gia đình có trẻ nhỏ, an toàn cho trẻ em",
        "elderly", "phù hợp người lớn tuổi, đi lại nhẹ nhàng",
        "limitedMobility", "ít leo dốc, không cần vận động mạnh",
        "disability", "đi lại thuận tiện, dễ tiếp cận",
        "quietSpace", "không gian yên tĩnh, ít đông",
        "largeGroup", "rộng rãi, phù hợp nhóm đông người",
        "vegetarian", "có món chay"
    );

    private static final Map<String, String> PACE_PHRASES = Map.of(
        "RELAXED", "nhịp độ thư giãn",
        "BALANCED", "nhịp độ cân bằng",
        "PACKED", "nhịp độ dày, khám phá nhiều nơi"
    );

    public String build(PlanProfile profile) {

        StringBuilder text = new StringBuilder(
            "Tôi muốn khám phá những địa điểm du lịch " +
            "có trải nghiệm phù hợp với sở thích của mình."
        );

        if (profile.categoryNames() != null
                && !profile.categoryNames().isEmpty()) {

            text.append(" Các loại trải nghiệm yêu thích: ")
                .append(String.join(", ", profile.categoryNames()))
                .append(".");
        }

        appendPhrases(text, " Tôi mong muốn: ", profile.experienceCodes(), EXPERIENCE_PHRASES);

        String companion = profile.companionType();
        if (companion != null
                && !companion.isBlank()
                && !"UNKNOWN".equalsIgnoreCase(companion)) {

            text.append(" Tôi đi du lịch theo hình thức: ")
                .append(companion)
                .append(".");
        }

        appendPhrases(
            text,
            " Yêu cầu đặc biệt: ",
            profile.specialNeeds() == null ? List.of() : List.copyOf(profile.specialNeeds()),
            SPECIAL_NEED_PHRASES
        );

        String budget = budgetLevel(profile);
        if (budget != null) {
            text.append(" Ngân sách ").append(budget).append(".");
        }

        String pace = profile.travelPace() == null ? null : PACE_PHRASES.get(profile.travelPace().toUpperCase());
        if (pace != null) {
            text.append(" Mong muốn ").append(pace).append(".");
        }

        return text.toString();
    }

    private void appendPhrases(StringBuilder text, String prefix, List<String> codes, Map<String, String> phrases) {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        Set<String> parts = new LinkedHashSet<>();
        for (String code : codes) {
            String phrase = code == null ? null : phrases.get(code);
            if (phrase != null) {
                parts.add(phrase);
            }
        }
        if (!parts.isEmpty()) {
            text.append(prefix).append(String.join("; ", parts)).append(".");
        }
    }

    /** Cùng ngưỡng với form lên kế hoạch: ngân sách mỗi người < 5 triệu / 5–12 triệu / > 12 triệu. */
    private String budgetLevel(PlanProfile profile) {
        if (!"VND".equalsIgnoreCase(profile.currency())) {
            return null;
        }
        BigDecimal perPerson = profile.budgetPerPerson();
        if (perPerson == null || perPerson.signum() <= 0) {
            return null;
        }
        if (perPerson.compareTo(BigDecimal.valueOf(5_000_000)) < 0) {
            return "tiết kiệm";
        }
        if (perPerson.compareTo(BigDecimal.valueOf(12_000_000)) <= 0) {
            return "tiêu chuẩn";
        }
        return "sang trọng";
    }
}
