package com.dtrps.modules.poi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.admin.service.AuditService;
import com.dtrps.modules.poi.dto.AdminCategoryGroup.AdminCategory;
import com.dtrps.modules.poi.dto.AdminCategoryRequest;
import com.dtrps.modules.poi.entity.PlaceCategory;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.poi.repository.PlaceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
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

/** Unit test AdminCategoryService — sheet Poi, UTC001–UTC007 trong Report5.1. */
@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTest {
  private static final Long ADMIN_ID = 1L;
  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Mock private PlaceCategoryRepository categories;
  @Mock private PlaceRepository places;
  @Mock private AuditService auditService;

  private AdminCategoryService service;
  private PlaceCategory fnb;
  private PlaceCategory cafe;

  @BeforeEach
  void setUp() {
    service = new AdminCategoryService(categories, places, auditService);
    fnb = new PlaceCategory();
    fnb.setCategoryId(2L);
    fnb.setCode("fnb");
    fnb.setName("Ẩm thực");
    cafe = new PlaceCategory();
    cafe.setCategoryId(20L);
    cafe.setCode("cafe");
    cafe.setName("Cà phê");
    cafe.setParent(fnb);
    lenient().when(categories.findByCode("fnb")).thenReturn(Optional.of(fnb));
    lenient().when(categories.findById(20L)).thenReturn(Optional.of(cafe));
    lenient().when(categories.findById(2L)).thenReturn(Optional.of(fnb));
  }

  private static AdminCategoryRequest request(String group, String code) {
    return new AdminCategoryRequest(group, code, " Cà phê làm việc ", null);
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
  @DisplayName("create")
  class Create {
    @Test
    @DisplayName("UTC001 Valid data → new ACTIVE category under the fnb group")
    void valid_creates() {
      assertThat(messages(request("fnb", "cafe_work"))).isEmpty();

      AdminCategory item = service.create(ADMIN_ID, request("fnb", "cafe_work"));

      ArgumentCaptor<PlaceCategory> captor = ArgumentCaptor.forClass(PlaceCategory.class);
      verify(categories).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getParent()).isSameAs(fnb);
      assertThat(captor.getValue().getName()).isEqualTo("Cà phê làm việc");
      assertThat(item.code()).isEqualTo("cafe_work");
      assertThat(item.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("UTC002 Code already exists → 409")
    void duplicateCode_conflict() {
      when(categories.existsByCodeIgnoreCase("cafe_work")).thenReturn(true);

      ApiException e = error(() -> service.create(ADMIN_ID, request("fnb", "cafe_work")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage()).isEqualTo("Mã loại đã tồn tại");
      verify(categories, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("UTC003 Unknown group → 400")
    void unknownGroup_badRequest() {
      ApiException e = error(() -> service.create(ADMIN_ID, request("abc", "cafe_work")));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage()).isEqualTo("Nhóm dịch vụ không hợp lệ");
    }

    @Test
    @DisplayName("UTC004 Code with uppercase letters → 400 (validation)")
    void uppercaseCode_invalid() {
      assertThat(messages(request("fnb", "CafeWork")))
          .containsExactly("Mã loại chỉ gồm chữ thường không dấu, số và dấu gạch dưới");
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {
    @Test
    @DisplayName("UTC005 Category not used by any place → deleted")
    void unused_deleted() {
      service.delete(ADMIN_ID, 20L);

      verify(categories).delete(cafe);
      verify(auditService).record(ADMIN_ID, "CATEGORY_DELETED", "place_categories", 20L, "cafe");
    }

    @Test
    @DisplayName("UTC006 Category used by places → 409")
    void inUse_conflict() {
      when(places.usesCategory(20L)).thenReturn(true);

      ApiException e = error(() -> service.delete(ADMIN_ID, 20L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
      assertThat(e.getMessage())
          .isEqualTo(
              "Loại hình đang có địa điểm sử dụng nên không xoá được — hãy tắt loại hình thay vì xoá");
      verify(categories, never()).delete(any());
    }

    @Test
    @DisplayName("UTC007 Root group (fnb) → 400")
    void rootGroup_badRequest() {
      ApiException e = error(() -> service.delete(ADMIN_ID, 2L));

      assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(e.getMessage())
          .isEqualTo("Không thể sửa nhóm dịch vụ, chỉ sửa được loại bên trong");
    }
  }
}
