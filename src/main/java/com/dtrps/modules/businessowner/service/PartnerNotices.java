package com.dtrps.modules.businessowner.service;

import com.dtrps.modules.businessowner.entity.BusinessListing;
import com.dtrps.modules.businessowner.entity.BusinessListingRevision;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Notifications of the partner flows: profile registration, place registration and change requests.
 * Partner actions notify the admins; admin decisions notify the partner and refresh the other
 * admins' screens. Links are the web pages that show the item.
 */
@Component
class PartnerNotices {
  private final NotificationService notifications;

  PartnerNotices(NotificationService notifications) {
    this.notifications = notifications;
  }

  // ===== Business profile =====

  void profileSubmitted(BusinessProfile profile, boolean resubmitted) {
    notifications.notifyAdmins(
        new Notice(
            resubmitted ? "PARTNER_RESUBMITTED" : "PARTNER_SUBMITTED",
            resubmitted ? "Hồ sơ đối tác gửi lại" : "Hồ sơ đối tác mới",
            profile.getBusinessName()
                + (resubmitted
                    ? " đã chỉnh sửa và gửi lại hồ sơ đăng ký đối tác."
                    : " vừa gửi hồ sơ đăng ký đối tác."),
            "/admin/partners/" + profile.getBusinessId(),
            profileData(profile)),
        null);
  }

  void profileUpdated(BusinessProfile profile) {
    notifications.refreshAdmins(
        new Notice(
            "PARTNER_UPDATED",
            "Hồ sơ đối tác được cập nhật",
            profile.getBusinessName() + " vừa cập nhật hồ sơ.",
            "/admin/partners/" + profile.getBusinessId(),
            profileData(profile)));
  }

  void profileDecided(BusinessProfile profile, Long adminId, String type, String reason) {
    String title;
    String message;
    String link = "/partner";
    switch (type) {
      case "PARTNER_APPROVED" -> {
        title = "Hồ sơ đối tác đã được duyệt";
        message =
            "Chúc mừng! "
                + profile.getBusinessName()
                + " đã trở thành đối tác. Bạn có thể đăng ký địa điểm ngay.";
      }
      case "PARTNER_REJECTED" -> {
        title = "Hồ sơ đối tác chưa được duyệt";
        message = "Lý do: " + reason + ". Vui lòng chỉnh sửa và gửi lại.";
        link = "/partner/apply";
      }
      case "PARTNER_SUSPENDED" -> {
        title = "Tài khoản đối tác bị tạm khoá";
        message = "Lý do: " + reason + ". Vui lòng liên hệ Admin để được hỗ trợ.";
      }
      default -> {
        title = "Tài khoản đối tác đã được mở khoá";
        message = profile.getBusinessName() + " có thể tiếp tục hoạt động bình thường.";
      }
    }
    Notice notice = new Notice(type, title, message, link, profileData(profile));
    notifications.notifyUser(profile.getUser().getUserId(), notice);
    notifications.refreshAdmins(notice);
  }

  // ===== Places =====

  void listingSubmitted(BusinessListing listing, boolean resubmitted) {
    String place = listing.getPlace().getName();
    String business = listing.getBusiness().getBusinessName();
    notifications.notifyAdmins(
        new Notice(
            resubmitted ? "LISTING_RESUBMITTED" : "LISTING_SUBMITTED",
            resubmitted ? "Địa điểm gửi lại để duyệt" : "Địa điểm mới chờ duyệt",
            business
                + (resubmitted ? " đã sửa và gửi lại địa điểm \"" : " vừa đăng ký địa điểm \"")
                + place
                + "\".",
            "/admin/listings/" + listing.getListingId(),
            listingData(listing)),
        null);
  }

  void listingUpdated(BusinessListing listing) {
    notifications.refreshAdmins(
        new Notice(
            "LISTING_UPDATED",
            "Địa điểm chờ duyệt được cập nhật",
            listing.getBusiness().getBusinessName()
                + " vừa cập nhật \""
                + listing.getPlace().getName()
                + "\".",
            "/admin/listings/" + listing.getListingId(),
            listingData(listing)));
  }

  void listingDecided(BusinessListing listing, boolean approved, String reason) {
    String place = listing.getPlace().getName();
    Notice notice =
        new Notice(
            approved ? "LISTING_APPROVED" : "LISTING_REJECTED",
            approved ? "Địa điểm đã được duyệt" : "Địa điểm chưa được duyệt",
            approved
                ? "\"" + place + "\" đã hiển thị với du khách."
                : "\"" + place + "\" bị từ chối. Lý do: " + reason + ".",
            "/partner/locations/" + listing.getListingId(),
            listingData(listing));
    notifications.notifyUser(listing.getBusiness().getUser().getUserId(), notice);
    notifications.refreshAdmins(notice);
  }

  // ===== Change requests =====

  void revisionSubmitted(BusinessListingRevision revision) {
    notifications.notifyAdmins(
        new Notice(
            "REVISION_SUBMITTED",
            "Đề xuất chỉnh sửa mới",
            revision.getListing().getBusiness().getBusinessName()
                + " đề xuất chỉnh sửa \""
                + revision.getListing().getPlace().getName()
                + "\".",
            "/admin/listings/revisions/" + revision.getRevisionId(),
            revisionData(revision)),
        null);
  }

  void revisionCancelled(BusinessListingRevision revision) {
    notifications.notifyAdmins(
        new Notice(
            "REVISION_CANCELLED",
            "Đối tác đã rút đề xuất",
            revision.getListing().getBusiness().getBusinessName()
                + " đã rút lại đề xuất chỉnh sửa \""
                + revision.getListing().getPlace().getName()
                + "\".",
            "/admin/listings/revisions/" + revision.getRevisionId(),
            revisionData(revision)),
        null);
  }

  void revisionDecided(BusinessListingRevision revision, boolean approved, String feedback) {
    String place = revision.getListing().getPlace().getName();
    Notice notice =
        new Notice(
            approved ? "REVISION_APPROVED" : "REVISION_REJECTED",
            approved ? "Đề xuất chỉnh sửa đã được duyệt" : "Đề xuất chỉnh sửa bị từ chối",
            approved
                ? "Thay đổi của \"" + place + "\" đã được áp dụng."
                : "Đề xuất cho \"" + place + "\" bị từ chối. Góp ý: " + feedback,
            "/partner/locations/" + revision.getListing().getListingId(),
            revisionData(revision));
    notifications.notifyUser(revision.getListing().getBusiness().getUser().getUserId(), notice);
    notifications.refreshAdmins(notice);
  }

  // ===== Data =====

  private static Map<String, Object> profileData(BusinessProfile profile) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("businessId", profile.getBusinessId());
    data.put("status", profile.getStatus());
    return data;
  }

  private static Map<String, Object> listingData(BusinessListing listing) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("businessId", listing.getBusiness().getBusinessId());
    data.put("listingId", listing.getListingId());
    data.put("placeId", listing.getPlace().getPlaceId());
    data.put("status", listing.getStatus());
    return data;
  }

  private static Map<String, Object> revisionData(BusinessListingRevision revision) {
    Map<String, Object> data = listingData(revision.getListing());
    data.put("revisionId", revision.getRevisionId());
    data.put("status", revision.getStatus());
    return data;
  }
}
