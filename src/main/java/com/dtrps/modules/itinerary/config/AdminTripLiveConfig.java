package com.dtrps.modules.itinerary.config;

import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Trang Quản lý chuyến đi của admin tự cập nhật: mỗi lần khách ghi thành công vào kế hoạch / lịch
 * trình / tiến độ (bắt đầu ngày, đánh dấu điểm đến, hoàn thành chuyến…) thì gửi một sự kiện làm mới
 * tới các tab admin. Chỉ làm mới màn hình, không tạo thông báo.
 */
@Configuration
public class AdminTripLiveConfig implements WebMvcConfigurer {
  static final String EVENT_TYPE = "TRIP_PLAN_CHANGED";

  private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final Pattern PLAN_PATH = Pattern.compile("^/api/travel-plans/(\\d+)");

  private final NotificationService notifications;

  public AdminTripLiveConfig(NotificationService notifications) {
    this.notifications = notifications;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public void afterCompletion(
                  HttpServletRequest request,
                  HttpServletResponse response,
                  Object handler,
                  Exception failure) {
                boolean succeeded = failure == null && response.getStatus() < 400;
                if (succeeded && !READ_METHODS.contains(request.getMethod())) {
                  notifications.refreshAdmins(notice(request.getRequestURI()));
                }
              }
            })
        .addPathPatterns("/api/travel-plans/**", "/api/itinerary-days/**");
  }

  /** {@code planId} có khi đường dẫn chứa mã kế hoạch; trang chi tiết dùng để lọc sự kiện. */
  private static Notice notice(String path) {
    Matcher plan = PLAN_PATH.matcher(path);
    Map<String, Object> data =
        plan.find() ? Map.of("planId", Long.valueOf(plan.group(1))) : Map.of();
    return new Notice(EVENT_TYPE, "Chuyến đi được cập nhật", "", null, data);
  }
}
