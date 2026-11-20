package com.dtrps.modules.businessowner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.businessowner.dto.BusinessProfileRequest;
import com.dtrps.modules.businessowner.dto.BusinessProfileResponse;
import com.dtrps.modules.businessowner.entity.BusinessProfile;
import com.dtrps.modules.businessowner.repository.BusinessProfileRepository;
import com.dtrps.modules.user.entity.Role;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.RoleRepository;
import com.dtrps.modules.user.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** Unit test BusinessProfileService — sheet BusinessOwner, UTC001–UTC019 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long OWNER_ID = 7L;
  private static final Long ADMIN_ID = 1L;
  private static final Long BUSINESS_ID = 3L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private BusinessProfileRepository profiles;
  @Mock private UserRepository users;
  @Mock private RoleRepository roles;
  @Mock private AuditService auditService;
  @Mock private PartnerNotices notices;

  private BusinessProfileService service;
  private User owner;
  private BusinessProfile profile;

  @BeforeEach
  void setUp() {
    service =
        new BusinessProfileService(
            profiles, users, roles, auditService, Clock.fixed(NOW, ZoneOffset.UTC), notices);
    owner = new User();
    owner.setUserId(OWNER_ID);
    profile = new BusinessProfile();
    profile.setBusinessId(BUSINESS_ID);
    profile.setUser(owner);
    profile.setBusinessName("Cty Du lịch A");
    profile.setTaxCode("0101234567");
    profile.setStatus("PENDING");
    lenient().when(profiles.findWithUserByBusinessId(BUSINESS_ID)).thenReturn(Optional.of(profile));
    lenient().when(profiles.findByUserUserId(OWNER_ID)).thenReturn(Optional.of(profile));
  }

  private static BusinessProfileRequest request(String taxCode) {
    return new BusinessProfileRequest(" Cty Du lịch A ", taxCode, "A@A.vn", "0912345678");
  }

  private static Set<String> messages(Object request) {
    return VALIDATOR.validate(request).stream()
        .map(ConstraintViolation::getMessage)
        .collect(Collectors.toSet());
  }

  private static ApiException error(Runnable action) {
    return catchThrowableOfType(action::run, ApiException.class);
  }

  @Nested
  @DisplayName("register")
  class Register {
    @Test
    @DisplayName("UTC001 User has no profile: profile PENDING, data trimmed, audit + notice")
    void newProfile_pending() {
      when(users.getReferenceById(OWNER_ID)).thenReturn(owner);

      BusinessProfileResponse response = service.register(OWNER_ID, request("0101234567"));

      ArgumentCaptor<BusinessProfile> captor = ArgumentCaptor.forClass(BusinessProfile.class);
      verify(profiles).saveAndFlush(captor.capture());
      BusinessProfile saved = captor.getValue();
      assertThat(saved.getStatus()).isEqualTo("PENDING");
      assertThat(saved.getBusinessName()).isEqualTo("Cty Du lịch A");
      assertThat(saved.getContactEmail()).isEqualTo("a@a.vn");
      assertThat(response.status()).isEqualTo("PENDING");
      verify(auditService)
          .record(eq(OWNER_ID), eq("BUSINESS_PROFILE_SUBMITTED"), anyString(), any());
      verify(notices).profileSubmitted(saved, false);
    }

    @Test
    @DisplayName("UTC002 User already has a profile → 409")
    void existingProfile_conflict() {
      when(profiles.existsByUserUserId(OWNER_ID)).thenReturn(true);

      ApiException e = error(() -> service.register(OWNER_ID, request("0101234567")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Bạn đã có hồ sơ doanh nghiệp");
    }

    @Test
    @DisplayName("UTC003 Tax code belongs to another business → 409")
    void takenTaxCode_conflict() {
      when(profiles.existsByTaxCode("0101234567")).thenReturn(true);

      ApiException e = error(() -> service.register(OWNER_ID, request("0101234567")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Mã số thuế đã được đăng ký bởi doanh nghiệp khác");
      verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UTC004 Tax code with branch suffix (0101234567-001) → valid")
    void branchTaxCode_valid() {
      assertThat(messages(request("0101234567-001"))).isEmpty();
      when(users.getReferenceById(OWNER_ID)).thenReturn(owner);

      BusinessProfileResponse response = service.register(OWNER_ID, request("0101234567-001"));

      assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("UTC005 Tax code with 9 digits → 400 (validation)")
    void nineDigitTaxCode_invalid() {
      assertThat(messages(request("010123456")))
          .containsExactly("Mã số thuế gồm 10 số, hoặc 10 số kèm mã chi nhánh dạng -001");
    }
  }

  @Nested
  @DisplayName("updateOwn")
  class UpdateOwn {
    @Test
    @DisplayName("UTC006 Rejected (INACTIVE) profile edited → back to PENDING")
    void rejected_resubmits() {
      profile.setStatus("INACTIVE");

      BusinessProfileResponse response = service.updateOwn(OWNER_ID, request("0101234567"));

      assertThat(profile.getStatus()).isEqualTo("PENDING");
      assertThat(response.status()).isEqualTo("PENDING");
      verify(notices).profileSubmitted(profile, true);
    }

    @Test
    @DisplayName("UTC007 User has no profile → 404")
    void noProfile_notFound() {
      when(profiles.findByUserUserId(OWNER_ID)).thenReturn(Optional.empty());

      ApiException e = error(() -> service.updateOwn(OWNER_ID, request("0101234567")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Bạn chưa đăng ký hồ sơ doanh nghiệp");
    }

    @Test
    @DisplayName("UTC008 Profile SUSPENDED → 409")
    void suspended_conflict() {
      profile.setStatus("SUSPENDED");

      ApiException e = error(() -> service.updateOwn(OWNER_ID, request("0101234567")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo("Hồ sơ doanh nghiệp đang bị khóa, vui lòng liên hệ quản trị viên");
    }
  }

  @Nested
  @DisplayName("approve")
  class Approve {
    @Test
    @DisplayName("UTC009 PENDING profile of another user → ACTIVE, BUSINESS_OWNER role granted")
    void pending_approvedAndRoleGranted() {
      Role role = new Role();
      role.setRoleName("BUSINESS_OWNER");
      when(roles.findByRoleName("BUSINESS_OWNER")).thenReturn(Optional.of(role));
      when(users.getReferenceById(ADMIN_ID)).thenReturn(new User());

      service.approve(ADMIN_ID, BUSINESS_ID);

      assertThat(profile.getStatus()).isEqualTo("ACTIVE");
      assertThat(profile.getApprovedAt()).isEqualTo(NOW);
      assertThat(owner.getRoles()).contains(role);
      verify(notices).profileDecided(profile, ADMIN_ID, "PARTNER_APPROVED", null);
    }

    @Test
    @DisplayName("UTC010 Admin approves own profile → 400")
    void ownProfile_badRequest() {
      ApiException e = error(() -> service.approve(OWNER_ID, BUSINESS_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Không thể tự duyệt hồ sơ của chính mình");
    }

    @Test
    @DisplayName("UTC011 Profile already ACTIVE → 409")
    void notPending_conflict() {
      profile.setStatus("ACTIVE");

      ApiException e = error(() -> service.approve(ADMIN_ID, BUSINESS_ID));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ có thể duyệt hoặc từ chối hồ sơ đang chờ duyệt");
    }

    @Test
    @DisplayName("UTC012 Unknown profile → 404")
    void missing_notFound() {
      ApiException e = error(() -> service.approve(ADMIN_ID, 999_999L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy hồ sơ doanh nghiệp");
    }
  }

  @Nested
  @DisplayName("reject")
  class Reject {
    @Test
    @DisplayName("UTC013 PENDING profile → INACTIVE, reason returned and audited")
    void pending_rejected() {
      BusinessProfileResponse response = service.reject(ADMIN_ID, BUSINESS_ID, " Sai mã số thuế ");

      assertThat(profile.getStatus()).isEqualTo("INACTIVE");
      assertThat(response.statusNote()).isEqualTo("Sai mã số thuế");
      verify(auditService)
          .record(
              ADMIN_ID,
              "BUSINESS_PROFILE_REJECTED",
              "BusinessProfile",
              BUSINESS_ID,
              "Sai mã số thuế");
    }

    @Test
    @DisplayName("UTC014 Profile already INACTIVE → 409")
    void notPending_conflict() {
      profile.setStatus("INACTIVE");

      ApiException e = error(() -> service.reject(ADMIN_ID, BUSINESS_ID, "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ có thể duyệt hoặc từ chối hồ sơ đang chờ duyệt");
    }
  }

  @Nested
  @DisplayName("updateStatus")
  class UpdateStatus {
    @Test
    @DisplayName("UTC015 ACTIVE profile locked with a reason → SUSPENDED")
    void lockActive() {
      profile.setStatus("ACTIVE");

      BusinessProfileResponse response =
          service.updateStatus(ADMIN_ID, BUSINESS_ID, "SUSPENDED", " Vi phạm ");

      assertThat(profile.getStatus()).isEqualTo("SUSPENDED");
      assertThat(response.statusNote()).isEqualTo("Vi phạm");
      verify(notices).profileDecided(profile, ADMIN_ID, "PARTNER_SUSPENDED", "Vi phạm");
    }

    @Test
    @DisplayName("UTC016 SUSPENDED profile unlocked → ACTIVE")
    void unlockSuspended() {
      profile.setStatus("SUSPENDED");

      service.updateStatus(ADMIN_ID, BUSINESS_ID, "ACTIVE", null);

      assertThat(profile.getStatus()).isEqualTo("ACTIVE");
      verify(notices).profileDecided(profile, ADMIN_ID, "PARTNER_REACTIVATED", null);
    }

    @Test
    @DisplayName("UTC017 Lock without a reason → 400")
    void lockWithoutReason_badRequest() {
      profile.setStatus("ACTIVE");

      ApiException e = error(() -> service.updateStatus(ADMIN_ID, BUSINESS_ID, "SUSPENDED", " "));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Vui lòng nhập lý do khóa");
      assertThat(profile.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("UTC018 Lock a PENDING profile → 409")
    void lockPending_conflict() {
      ApiException e = error(() -> service.updateStatus(ADMIN_ID, BUSINESS_ID, "SUSPENDED", "x"));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ có thể khóa hồ sơ đang hoạt động");
    }

    @Test
    @DisplayName("UTC019 Unlock an ACTIVE profile → 409")
    void unlockActive_conflict() {
      profile.setStatus("ACTIVE");

      ApiException e = error(() -> service.updateStatus(ADMIN_ID, BUSINESS_ID, "ACTIVE", null));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Chỉ có thể mở khóa hồ sơ đang bị khóa");
      verify(notices, never()).profileDecided(any(), anyLong(), anyString(), any());
      verify(notices, never()).profileSubmitted(any(), anyBoolean());
    }
  }
}
