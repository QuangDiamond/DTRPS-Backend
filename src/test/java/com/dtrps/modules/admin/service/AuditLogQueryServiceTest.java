package com.dtrps.modules.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dtrps.common.PageResponse;
import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.dto.AuditDtos.AuditLogItem;
import com.dtrps.modules.admin.entity.AuditLog;
import com.dtrps.modules.admin.repository.AuditLogRepository;
import com.dtrps.modules.user.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/**
 * Unit test AuditLogQueryService.search — sheet Admin, UTC001–UTC004 trong Report5.1. Câu lọc
 * (Specification) chạy trên DB nên ở đây chỉ kiểm tra phần kiểm tra khoảng ngày và cách đổi kết
 * quả.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogQueryServiceTest {
  private static final Pageable NEWEST_FIRST =
      PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "logId"));

  @Mock private AuditLogRepository logs;

  private AuditLogQueryService service;

  @BeforeEach
  void setUp() {
    service = new AuditLogQueryService(logs);
    User admin = new User();
    admin.setUserId(1L);
    admin.setFullName("Nam Admin");
    admin.setEmail("admin@smarttravel.vn");
    AuditLog newer = log(3L, "PARTNER_APPROVED", admin);
    AuditLog system = log(2L, "PAYOS_JOB", null);
    lenient()
        .when(logs.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(newer, system), NEWEST_FIRST, 2));
  }

  private static AuditLog log(Long id, String action, User user) {
    AuditLog log = new AuditLog();
    log.setLogId(id);
    log.setAction(action);
    log.setEntityName("BusinessProfile");
    log.setEntityId(10L);
    log.setDescription("duyệt hồ sơ");
    log.setUser(user);
    log.setCreatedAt(Instant.parse("2026-11-15T03:00:00Z"));
    return log;
  }

  private ApiException error(LocalDate from, LocalDate to) {
    return catchThrowableOfType(
        () -> service.search(null, null, null, from, to, NEWEST_FIRST), ApiException.class);
  }

  @Test
  @DisplayName("UTC001 All filters in November → page mapped with actor, newest-first paging kept")
  void filters_mapped() {
    PageResponse<AuditLogItem> page =
        service.search(
            "duyệt",
            "PARTNER_APPROVED",
            1L,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 30),
            NEWEST_FIRST);

    verify(logs).findAll(any(Specification.class), eq(NEWEST_FIRST));
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items()).extracting(AuditLogItem::id).containsExactly(3L, 2L);
    assertThat(page.items().get(0).actorName()).isEqualTo("Nam Admin");
    assertThat(page.items().get(1).actorId()).isNull();
  }

  @Test
  @DisplayName("UTC002 End date before start date → 400")
  void reversedRange_badRequest() {
    ApiException e = error(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Ngày kết thúc phải sau ngày bắt đầu");
    verifyNoInteractions(logs);
  }

  @Test
  @DisplayName("UTC003 Range of 367 days → 400; exactly 366 days is still accepted")
  void overOneYear_badRequest() {
    ApiException e = error(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 11, 3));

    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).isEqualTo("Chỉ lọc tối đa 1 năm mỗi lần");
    assertThat(
            service
                .search(
                    null,
                    null,
                    null,
                    LocalDate.of(2025, 11, 1),
                    LocalDate.of(2026, 11, 2),
                    NEWEST_FIRST)
                .items())
        .hasSize(2);
  }

  @Test
  @DisplayName("UTC004 from = to (a single day) → accepted")
  void singleDay_accepted() {
    LocalDate day = LocalDate.of(2026, 11, 19);

    assertThat(service.search(null, null, null, day, day, NEWEST_FIRST).items()).hasSize(2);
  }
}
