package com.dtrps.modules.admin.service;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.SettingsDtos.AnnouncementSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.GeneralSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.PublicAnnouncement;
import com.dtrps.modules.admin.dto.SettingsDtos.PublicLimits;
import com.dtrps.modules.admin.dto.SettingsDtos.RuleSettings;
import com.dtrps.modules.admin.dto.SettingsDtos.SectionMeta;
import com.dtrps.modules.admin.dto.SettingsDtos.SettingsView;
import com.dtrps.modules.admin.dto.SettingsDtos.SiteInfo;
import com.dtrps.modules.admin.entity.SystemSetting;
import com.dtrps.modules.admin.repository.SystemSettingRepository;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Settings admins edit on /admin/settings, stored one row per key in system_settings. Every value
 * has a default here, so a fresh database works without any row. Reads are cached for a short time:
 * jobs and uploads ask often, and other servers' saves show up within {@link #CACHE_TTL}.
 */
@Service
public class SystemSettingsService {
  static final Duration CACHE_TTL = Duration.ofSeconds(30);

  // general
  static final String SITE_NAME = "general.site_name";
  static final String SUPPORT_EMAIL = "general.support_email";
  static final String SUPPORT_PHONE = "general.support_phone";
  static final String SUPPORT_HOURS = "general.support_hours";
  // rules
  static final String MAX_PLACE_IMAGES = "rules.max_place_images";
  static final String MAX_IMAGE_MB = "rules.max_image_mb";
  static final String RENEWAL_NOTICE_DAYS = "rules.renewal_notice_days";
  static final String PAYMENT_LINK_MINUTES = "rules.payment_link_minutes";
  // announcement
  static final String BANNER_ENABLED = "announcement.enabled";
  static final String BANNER_MESSAGE = "announcement.message";
  static final String BANNER_LEVEL = "announcement.level";
  static final String BANNER_AUDIENCE = "announcement.audience";
  static final String BANNER_STARTS_AT = "announcement.starts_at";
  static final String BANNER_ENDS_AT = "announcement.ends_at";
  // internal (not on the form)
  static final String PAYOS_LAST_WEBHOOK = "internal.payos_last_webhook_at";

  private static final List<String> GENERAL_KEYS =
      List.of(SITE_NAME, SUPPORT_EMAIL, SUPPORT_PHONE, SUPPORT_HOURS);
  private static final List<String> RULE_KEYS =
      List.of(MAX_PLACE_IMAGES, MAX_IMAGE_MB, RENEWAL_NOTICE_DAYS, PAYMENT_LINK_MINUTES);
  private static final List<String> BANNER_KEYS =
      List.of(
          BANNER_ENABLED,
          BANNER_MESSAGE,
          BANNER_LEVEL,
          BANNER_AUDIENCE,
          BANNER_STARTS_AT,
          BANNER_ENDS_AT);

  private static final Map<String, String> DEFAULTS =
      Map.ofEntries(
          Map.entry(SITE_NAME, "SmartTravel"),
          Map.entry(SUPPORT_EMAIL, "support@smarttravel.vn"),
          Map.entry(SUPPORT_PHONE, ""),
          Map.entry(SUPPORT_HOURS, ""),
          Map.entry(MAX_PLACE_IMAGES, "10"),
          Map.entry(MAX_IMAGE_MB, "5"),
          Map.entry(RENEWAL_NOTICE_DAYS, "7"),
          Map.entry(PAYMENT_LINK_MINUTES, "15"),
          Map.entry(BANNER_ENABLED, "false"),
          Map.entry(BANNER_MESSAGE, ""),
          Map.entry(BANNER_LEVEL, "INFO"),
          Map.entry(BANNER_AUDIENCE, "ALL"),
          Map.entry(BANNER_STARTS_AT, ""),
          Map.entry(BANNER_ENDS_AT, ""));

  /** Mobile / landline (0… or +84…, 10–11 digits) or a 1800 / 1900 hotline */
  private static final Pattern PHONE =
      Pattern.compile("^(?:(?:\\+84|0)[1-9]\\d{8,9}|1[89]00\\d{4,6})$");

  private static final int MESSAGE_MIN = 10;

  private record Row(String value, Instant updatedAt, String updatedBy) {}

  private record Snapshot(Map<String, Row> rows, Instant loadedAt) {}

  private final SystemSettingRepository repository;
  private final UserRepository users;
  private final AuditService auditService;
  private final NotificationService notifications;
  private final Clock clock;
  private volatile Snapshot snapshot;

  public SystemSettingsService(
      SystemSettingRepository repository,
      UserRepository users,
      AuditService auditService,
      NotificationService notifications,
      Clock clock) {
    this.repository = repository;
    this.users = users;
    this.auditService = auditService;
    this.notifications = notifications;
    this.clock = clock;
  }

  // ===== Typed values used across the system =====

  public int maxPlaceImages() {
    return intValue(MAX_PLACE_IMAGES);
  }

  public long maxImageBytes() {
    return intValue(MAX_IMAGE_MB) * 1024L * 1024L;
  }

  public int maxImageMb() {
    return intValue(MAX_IMAGE_MB);
  }

  public Duration renewalNotice() {
    return Duration.ofDays(intValue(RENEWAL_NOTICE_DAYS));
  }

  public Duration paymentLinkTtl() {
    return Duration.ofMinutes(intValue(PAYMENT_LINK_MINUTES));
  }

  public Instant payOsLastWebhookAt() {
    return instantValue(PAYOS_LAST_WEBHOOK);
  }

  /** Called by the PayOS webhook so the settings page can show the link works. */
  @Transactional
  public void markPayOsWebhook() {
    write(PAYOS_LAST_WEBHOOK, clock.instant().toString(), null);
    invalidate();
  }

  // ===== Admin page =====

  public SettingsView view() {
    Map<String, Row> rows = rows();
    return new SettingsView(
        general(),
        meta(rows, GENERAL_KEYS),
        rules(),
        meta(rows, RULE_KEYS),
        announcement(),
        meta(rows, BANNER_KEYS),
        announcementState(announcement(), clock.instant()));
  }

  @Transactional
  public SettingsView saveGeneral(Long adminId, GeneralSettings request) {
    String phone = request.supportPhone().trim();
    if (!PHONE.matcher(phone.replaceAll("[\\s.()-]", "")).matches()) {
      throw badRequest(
          "Hotline không hợp lệ: số di động/cố định 10–11 số (0… hoặc +84…) hoặc đầu số 1800/1900");
    }
    Map<String, String> values = new LinkedHashMap<>();
    values.put(SITE_NAME, request.siteName().trim().replaceAll("\\s+", " "));
    values.put(SUPPORT_EMAIL, request.supportEmail().trim().toLowerCase(Locale.ROOT));
    values.put(SUPPORT_PHONE, phone);
    values.put(SUPPORT_HOURS, request.supportHours() == null ? "" : request.supportHours().trim());
    save(adminId, "general", values);
    return view();
  }

  @Transactional
  public SettingsView saveRules(Long adminId, RuleSettings request) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(MAX_PLACE_IMAGES, String.valueOf(request.maxPlaceImages()));
    values.put(MAX_IMAGE_MB, String.valueOf(request.maxImageMb()));
    values.put(RENEWAL_NOTICE_DAYS, String.valueOf(request.renewalNoticeDays()));
    values.put(PAYMENT_LINK_MINUTES, String.valueOf(request.paymentLinkMinutes()));
    save(adminId, "rules", values);
    return view();
  }

  @Transactional
  public SettingsView saveAnnouncement(Long adminId, AnnouncementSettings request) {
    String message = request.message() == null ? "" : request.message().trim();
    Instant now = clock.instant();
    if (request.enabled()) {
      if (message.length() < MESSAGE_MIN) {
        throw badRequest("Nội dung thông báo cần ít nhất " + MESSAGE_MIN + " ký tự");
      }
      if (request.endsAt() != null && !request.endsAt().isAfter(now)) {
        throw badRequest("Thời gian kết thúc phải ở tương lai");
      }
    }
    if (request.startsAt() != null
        && request.endsAt() != null
        && !request.endsAt().isAfter(request.startsAt())) {
      throw badRequest("Thời gian kết thúc phải sau thời gian bắt đầu");
    }
    Map<String, String> values = new LinkedHashMap<>();
    values.put(BANNER_ENABLED, String.valueOf(request.enabled()));
    values.put(BANNER_MESSAGE, message);
    values.put(BANNER_LEVEL, request.level());
    values.put(BANNER_AUDIENCE, request.audience());
    values.put(BANNER_STARTS_AT, request.startsAt() == null ? "" : request.startsAt().toString());
    values.put(BANNER_ENDS_AT, request.endsAt() == null ? "" : request.endsAt().toString());
    if (save(adminId, "announcement", values)) {
      // Tab của du khách / đối tác đang mở tải lại banner ngay
      notifications.refreshEveryone(
          new Notice("SITE_ANNOUNCEMENT_CHANGED", "", "", null, Map.of()));
    }
    return view();
  }

  // ===== Public =====

  public SiteInfo siteInfo() {
    GeneralSettings general = general();
    AnnouncementSettings banner = announcement();
    Instant now = clock.instant();
    PublicAnnouncement live = null;
    if ("LIVE".equals(announcementState(banner, now))) {
      Row row = rows().get(BANNER_MESSAGE);
      long version = row == null || row.updatedAt() == null ? 0 : row.updatedAt().toEpochMilli();
      live =
          new PublicAnnouncement(
              banner.message(), banner.level(), banner.audience(), banner.endsAt(), version);
    }
    return new SiteInfo(
        general.siteName(),
        general.supportEmail(),
        general.supportPhone(),
        general.supportHours(),
        new PublicLimits(maxPlaceImages(), maxImageMb()),
        live);
  }

  // ===== Helpers =====

  private GeneralSettings general() {
    return new GeneralSettings(
        value(SITE_NAME), value(SUPPORT_EMAIL), value(SUPPORT_PHONE), value(SUPPORT_HOURS));
  }

  private RuleSettings rules() {
    return new RuleSettings(
        intValue(MAX_PLACE_IMAGES),
        intValue(MAX_IMAGE_MB),
        intValue(RENEWAL_NOTICE_DAYS),
        intValue(PAYMENT_LINK_MINUTES));
  }

  private AnnouncementSettings announcement() {
    return new AnnouncementSettings(
        Boolean.parseBoolean(value(BANNER_ENABLED)),
        value(BANNER_MESSAGE),
        value(BANNER_LEVEL),
        value(BANNER_AUDIENCE),
        instantValue(BANNER_STARTS_AT),
        instantValue(BANNER_ENDS_AT));
  }

  static String announcementState(AnnouncementSettings banner, Instant now) {
    if (!banner.enabled() || banner.message() == null || banner.message().isBlank()) {
      return "OFF";
    }
    if (banner.endsAt() != null && !banner.endsAt().isAfter(now)) {
      return "ENDED";
    }
    if (banner.startsAt() != null && banner.startsAt().isAfter(now)) {
      return "SCHEDULED";
    }
    return "LIVE";
  }

  /** Writes the changed keys, audits "key: old → new"; false when nothing changed. */
  private boolean save(Long adminId, String section, Map<String, String> values) {
    List<String> changes = new ArrayList<>();
    values.forEach(
        (key, next) -> {
          String current = value(key);
          if (!Objects.equals(current, next)) {
            write(key, next, adminId);
            changes.add(key + ": " + shorten(current) + " → " + shorten(next));
          }
        });
    invalidate();
    if (changes.isEmpty()) {
      return false;
    }
    auditService.record(
        adminId,
        "SETTINGS_UPDATED",
        "system_settings",
        null,
        section + " | " + String.join("; ", changes));
    notifications.refreshAdmins(
        new Notice("SETTINGS_UPDATED", "", "", null, Map.of("section", section)));
    return true;
  }

  private void write(String key, String value, Long adminId) {
    SystemSetting setting = repository.findById(key).orElseGet(SystemSetting::new);
    setting.setKey(key);
    setting.setValue(value);
    setting.setUpdatedBy(adminId == null ? null : users.getReferenceById(adminId));
    repository.save(setting);
  }

  private static String shorten(String value) {
    if (value == null || value.isEmpty()) {
      return "(trống)";
    }
    return value.length() > 60 ? value.substring(0, 57) + "…" : value;
  }

  private String value(String key) {
    Row row = rows().get(key);
    return row == null ? DEFAULTS.getOrDefault(key, "") : row.value();
  }

  private int intValue(String key) {
    try {
      return Integer.parseInt(value(key).trim());
    } catch (NumberFormatException e) {
      return Integer.parseInt(DEFAULTS.get(key));
    }
  }

  private Instant instantValue(String key) {
    String raw = value(key);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static SectionMeta meta(Map<String, Row> rows, List<String> keys) {
    Row latest = null;
    for (String key : keys) {
      Row row = rows.get(key);
      if (row != null
          && row.updatedAt() != null
          && (latest == null || row.updatedAt().isAfter(latest.updatedAt()))) {
        latest = row;
      }
    }
    return latest == null
        ? new SectionMeta(null, null)
        : new SectionMeta(latest.updatedAt(), latest.updatedBy());
  }

  private Map<String, Row> rows() {
    Snapshot current = snapshot;
    Instant now = clock.instant();
    if (current == null || current.loadedAt().plus(CACHE_TTL).isBefore(now)) {
      Map<String, Row> loaded = new HashMap<>();
      for (SystemSetting setting : repository.findAllWithUpdater()) {
        loaded.put(
            setting.getKey(),
            new Row(
                setting.getValue(),
                setting.getUpdatedAt(),
                setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getFullName()));
      }
      current = new Snapshot(Map.copyOf(loaded), now);
      snapshot = current;
    }
    return current.rows();
  }

  /** Drops the cache now and again after commit, so a read in between cannot keep old values. */
  private void invalidate() {
    snapshot = null;
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              snapshot = null;
            }
          });
    }
  }

  private static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }
}
