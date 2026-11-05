package com.dtrps.modules.itinerary.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Trạng thái của kế hoạch du lịch và các bước chuyển hợp lệ.
 *
 * <p>DRAFT → PLANNED → IN_PROGRESS → COMPLETED. Hủy (CANCELLED) được ở mọi bước trước COMPLETED.
 * Không cho đi ngược, không nhảy cóc. COMPLETED và CANCELLED là trạng thái cuối.
 */
public final class TravelPlanStatus {

  public static final String DRAFT = "DRAFT";
  public static final String PLANNED = "PLANNED";
  public static final String IN_PROGRESS = "IN_PROGRESS";
  public static final String COMPLETED = "COMPLETED";
  public static final String CANCELLED = "CANCELLED";

  /** Thứ tự cố định để trả về cho client. */
  public static final List<String> ALL =
      List.of(DRAFT, PLANNED, IN_PROGRESS, COMPLETED, CANCELLED);

  private static final Map<String, List<String>> NEXT =
      Map.of(
          DRAFT, List.of(PLANNED, CANCELLED),
          PLANNED, List.of(IN_PROGRESS, CANCELLED),
          IN_PROGRESS, List.of(COMPLETED, CANCELLED),
          COMPLETED, List.of(),
          CANCELLED, List.of());

  /** Chỉ sửa nội dung kế hoạch khi chuyến đi chưa bắt đầu. */
  private static final Set<String> EDITABLE = Set.of(DRAFT, PLANNED);

  private TravelPlanStatus() {}

  public static boolean isKnown(String status) {
    return status != null && NEXT.containsKey(status);
  }

  /** Các trạng thái được phép chuyển tới từ {@code from}. Trạng thái lạ trả danh sách rỗng. */
  public static List<String> allowedNext(String from) {
    return from == null ? List.of() : NEXT.getOrDefault(from, List.of());
  }

  public static boolean canTransition(String from, String to) {
    return allowedNext(from).contains(to);
  }

  public static boolean isEditable(String status) {
    return status != null && EDITABLE.contains(status);
  }

  /** Không cho xóa khi chuyến đi đang diễn ra. */
  public static boolean isDeletable(String status) {
    return !IN_PROGRESS.equals(status);
  }
}
