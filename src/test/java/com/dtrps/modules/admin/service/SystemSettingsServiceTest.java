package com.dtrps.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.SettingsDtos.AnnouncementSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.GeneralSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.RuleSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.SettingsView;
import com.dtrps.modules.admin.dto.SettingsDtos.SiteInfo;
import com.dtrps.modules.admin.entity.SystemSetting;
import com.dtrps.modules.admin.repository.SystemSettingRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Unit test SystemSettingsService — sheet Admin, UTC010–UTC032 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long ADMIN_ID = 1L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private SystemSettingRepository repository;
  @Mock private UserRepository users;
  @Mock private AuditService auditService;
  @Mock private NotificationService notifications;

  private SystemSettingsService service;

  /** Bảng system_settings giả trong bộ nhớ */
  private final Map<String, SystemSetting> table = new HashMap<>();

  @BeforeEach
  void setUp() {
    service =
        new SystemSettingsService(
            repository, users, auditService, notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(repository.findAllWithUpdater())
        .thenAnswer(call -> new ArrayList<>(table.values()));
    lenient()
        .when(repository.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(table.get((String) call.getArgument(0))));
    lenient()
        .when(repository.save(any(SystemSetting.class)))
        .thenAnswer(
            call -> {
              SystemSetting setting = call.getArgument(0);
              setting.setUpdatedAt(NOW);
              table.put(setting.getKey(), setting);
              return setting;
            });
    User admin = new User();
    admin.setFullName("Nam Admin");
    lenient().when(users.getReferenceById(ADMIN_ID)).thenReturn(admin);
  }

  private void stored(String key, String value) {
    SystemSetting setting = new SystemSetting();
    setting.setKey(key);
    setting.setValue(value);
    setting.setUpdatedAt(NOW.minus(Duration.ofDays(1)));
    table.put(key, setting);
  }

  private void banner(boolean enabled, Instant startsAt, Instant endsAt) {
    stored("announcement.enabled", String.valueOf(enabled));
    stored("announcement.message", "Hệ thống bảo trì lúc 22h");
    stored("announcement.starts_at", startsAt == null ? "" : startsAt.toString());
    stored("announcement.ends_at", endsAt == null ? "" : endsAt.toString());
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  private static GeneralSettings general(String siteName, String email, String phone) {
    return new GeneralSettings(siteName, email, phone, "8:00–17:00");
  }

  private static AnnouncementSettings announcement(
      String message, String level, Instant startsAt, Instant endsAt) {
    return new AnnouncementSettings(true, message, level, "ALL", startsAt, endsAt);
  }

  @Nested
  @DisplayName("view")
  class View {
    @Test
    @DisplayName("UTC010 Empty table → default values, no meta")
    void empty_defaults() {
      SettingsView view = service.view();

      assertThat(view.general().siteName()).isEqualTo("SmartTravel");
      assertThat(view.rules()).isEqualTo(new RuleSettings(10, 5, 7, 15));
      assertThat(view.generalMeta().updatedAt()).isNull();
      assertThat(view.announcementState()).isEqualTo("OFF");
    }

    @Test
    @DisplayName("UTC011 max_place_images stored as \"abc\" → falls back to 10")
    void corrupted_defaults() {
      stored("rules.max_place_images", "abc");

      assertThat(service.view().rules().maxPlaceImages()).isEqualTo(10);
    }

    @Test
    @DisplayName("UTC012 Banner enabled, starts in the future → SCHEDULED")
    void future_scheduled() {
      banner(true, NOW.plus(Duration.ofHours(2)), null);

      assertThat(service.view().announcementState()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("UTC013 Banner enabled, ended in the past → ENDED")
    void past_ended() {
      banner(true, null, NOW.minusSeconds(1));

      assertThat(service.view().announcementState()).isEqualTo("ENDED");
    }
  }

  @Nested
  @DisplayName("saveGeneral")
  class SaveGeneral {
    @Test
    @DisplayName("UTC014 Valid data → saved (email lower-cased), audit SETTINGS_UPDATED")
    void valid_saved() {
      GeneralSettings request = general("SmartTravel  VN", "Support@SmartTravel.vn", "0912345678");
      assertThat(messages(request)).isEmpty();

      SettingsView view = service.saveGeneral(ADMIN_ID, request);

      assertThat(view.general().siteName()).isEqualTo("SmartTravel VN");
      assertThat(view.general().supportEmail()).isEqualTo("support@smarttravel.vn");
      assertThat(view.generalMeta().updatedBy()).isEqualTo("Nam Admin");
      verify(auditService)
          .record(
              eq(ADMIN_ID), eq("SETTINGS_UPDATED"), eq("system_settings"), isNull(), anyString());
    }

    @Test
    @DisplayName("UTC015 Hotline \"12345\" → 400, nothing saved")
    void badPhone_badRequest() {
      ApiException e =
          error(() -> service.saveGeneral(ADMIN_ID, general("SmartTravel", "a@a.vn", "12345")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage())
          .isEqualTo(
              "Hotline không hợp lệ: số di động/cố định 10–11 số (0… hoặc +84…) hoặc đầu số 1800/1900");
      assertThat(table).isEmpty();
    }

    @Test
    @DisplayName("UTC016 Support email \"abc\" → 400 (validation)")
    void badEmail_invalid() {
      assertThat(messages(general("SmartTravel", "abc", "0912345678")))
          .containsExactly("Email hỗ trợ không hợp lệ");
    }

    @Test
    @DisplayName("UTC017 Site name of 61 characters → 400 (validation); 60 accepted")
    void longName_invalid() {
      assertThat(messages(general("a".repeat(61), "a@a.vn", "0912345678")))
          .containsExactly("Tên hệ thống dài 2–60 ký tự");
      assertThat(messages(general("a".repeat(60), "a@a.vn", "0912345678"))).isEmpty();
    }

    @Test
    @DisplayName("UTC018 Hotline 19001234 → saved")
    void hotline1900_saved() {
      SettingsView view =
          service.saveGeneral(ADMIN_ID, general("SmartTravel", "a@a.vn", "19001234"));

      assertThat(view.general().supportPhone()).isEqualTo("19001234");
    }
  }

  @Nested
  @DisplayName("saveRules")
  class SaveRules {
    @Test
    @DisplayName("UTC019 maxPlaceImages 10 → 12: saved, audit shows \"10 → 12\"")
    void change_audited() {
      service.saveRules(ADMIN_ID, new RuleSettings(12, 5, 7, 15));

      assertThat(service.maxPlaceImages()).isEqualTo(12);
      ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
      verify(auditService)
          .record(
              eq(ADMIN_ID), eq("SETTINGS_UPDATED"), anyString(), isNull(), description.capture());
      assertThat(description.getValue()).isEqualTo("rules | rules.max_place_images: 10 → 12");
    }

    @Test
    @DisplayName("UTC020 maxImageMb missing → 400 (validation)")
    void missingMb_invalid() {
      assertThat(messages(new RuleSettings(10, null, 7, 15)))
          .containsExactly("Vui lòng nhập dung lượng ảnh tối đa");
    }

    @Test
    @DisplayName("UTC021 maxPlaceImages 21 → 400 (validation); 20 accepted")
    void imagesOverMax_invalid() {
      assertThat(messages(new RuleSettings(21, 5, 7, 15)))
          .containsExactly("Số ảnh tối đa từ 1 đến 20");
      assertThat(messages(new RuleSettings(20, 5, 7, 15))).isEmpty();
    }

    @Test
    @DisplayName("UTC022 paymentLinkMinutes 4 → 400 (validation); 5 accepted")
    void linkBelowMin_invalid() {
      assertThat(messages(new RuleSettings(10, 5, 7, 4)))
          .containsExactly("Link thanh toán hết hạn sau 5 đến 60 phút");
      assertThat(messages(new RuleSettings(10, 5, 7, 5))).isEmpty();
    }

    @Test
    @DisplayName("UTC023 Same values as stored → no audit, no refresh")
    void unchanged_noAudit() {
      service.saveRules(ADMIN_ID, new RuleSettings(10, 5, 7, 15));

      verify(auditService, never()).record(anyLong(), anyString(), anyString(), any(), anyString());
      verify(notifications, never()).refreshAdmins(any());
    }
  }

  @Nested
  @DisplayName("saveAnnouncement")
  class SaveAnnouncement {
    @Test
    @DisplayName("UTC024 Enabled with a valid message → LIVE, every tab refreshed")
    void valid_live() {
      SettingsView view =
          service.saveAnnouncement(
              ADMIN_ID, announcement("Hệ thống bảo trì 22h", "INFO", null, null));

      assertThat(view.announcementState()).isEqualTo("LIVE");
      ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
      verify(notifications).refreshEveryone(notice.capture());
      assertThat(notice.getValue().type()).isEqualTo("SITE_ANNOUNCEMENT_CHANGED");
    }

    @Test
    @DisplayName("UTC025 Message of 7 characters → 400")
    void shortMessage_badRequest() {
      ApiException e =
          error(
              () ->
                  service.saveAnnouncement(ADMIN_ID, announcement("Bảo trì", "INFO", null, null)));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Nội dung thông báo cần ít nhất 10 ký tự");
    }

    @Test
    @DisplayName("UTC026 level=\"HIGH\" → 400 (validation)")
    void badLevel_invalid() {
      assertThat(messages(announcement("Hệ thống bảo trì 22h", "HIGH", null, null)))
          .containsExactly("Mức độ không hợp lệ");
    }

    @Test
    @DisplayName("UTC027 endsAt before startsAt (both in the future) → 400")
    void endBeforeStart_badRequest() {
      ApiException e =
          error(
              () ->
                  service.saveAnnouncement(
                      ADMIN_ID,
                      announcement(
                          "Hệ thống bảo trì 22h",
                          "INFO",
                          NOW.plus(Duration.ofHours(5)),
                          NOW.plus(Duration.ofHours(2)))));

      assertThat(e.getMessage()).isEqualTo("Thời gian kết thúc phải sau thời gian bắt đầu");
    }

    @Test
    @DisplayName("UTC028 endsAt = now → 400")
    void endNow_badRequest() {
      ApiException e =
          error(
              () ->
                  service.saveAnnouncement(
                      ADMIN_ID, announcement("Hệ thống bảo trì 22h", "INFO", null, NOW)));

      assertThat(e.getMessage()).isEqualTo("Thời gian kết thúc phải ở tương lai");
    }

    @Test
    @DisplayName("UTC029 Message of 301 characters → 400 (validation); 300 accepted")
    void longMessage_invalid() {
      assertThat(messages(announcement("a".repeat(301), "INFO", null, null)))
          .containsExactly("Nội dung thông báo tối đa 300 ký tự");
      assertThat(messages(announcement("a".repeat(300), "INFO", null, null))).isEmpty();
    }
  }

  @Nested
  @DisplayName("siteInfo")
  class SiteInfoTests {
    @Test
    @DisplayName("UTC030 Banner LIVE → public announcement with a version")
    void live_announcement() {
      banner(true, null, NOW.plus(Duration.ofHours(3)));

      SiteInfo info = service.siteInfo();

      assertThat(info.announcement()).isNotNull();
      assertThat(info.announcement().message()).isEqualTo("Hệ thống bảo trì lúc 22h");
      assertThat(info.announcement().version())
          .isEqualTo(NOW.minus(Duration.ofDays(1)).toEpochMilli());
    }

    @Test
    @DisplayName("UTC031 max_image_mb stored as \"x\" → limits fall back to 5 MB")
    void corrupted_defaults() {
      stored("rules.max_image_mb", "x");

      assertThat(service.siteInfo().limits().maxImageMb()).isEqualTo(5);
    }

    @Test
    @DisplayName("UTC032 Banner disabled → no announcement")
    void disabled_none() {
      banner(false, null, null);

      assertThat(service.siteInfo().announcement()).isNull();
    }
  }
}
