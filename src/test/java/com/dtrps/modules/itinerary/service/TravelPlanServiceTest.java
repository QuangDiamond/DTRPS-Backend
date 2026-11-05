package com.dtrps.modules.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.itinerary.dto.TravelPlanDtos;
import com.dtrps.modules.itinerary.entity.PlanInterest;
import com.dtrps.modules.itinerary.entity.TravelPlan;
import com.dtrps.modules.itinerary.repository.ItineraryRepository;
import com.dtrps.modules.itinerary.repository.PlanInterestRepository;
import com.dtrps.modules.itinerary.repository.TravelPlanRepository;
import com.dtrps.modules.poi.entity.Interest;
import com.dtrps.modules.poi.repository.InterestRepository;
import com.dtrps.modules.poi.repository.PlaceCategoryRepository;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TravelPlanServiceTest {

  private static final Long OWNER_ID = 10L;
  private static final Long OTHER_ID = 99L;
  private static final Long PLAN_ID = 5L;
  private static final LocalDate START = LocalDate.of(2026, 12, 1);
  private static final LocalDate END = LocalDate.of(2026, 12, 3);

  @Mock private TravelPlanRepository planRepository;
  @Mock private PlanInterestRepository planInterestRepository;
  @Mock private ItineraryRepository itineraryRepository;
  @Mock private UserRepository userRepository;
  @Mock private PlaceCategoryRepository categoryRepository;
  @Mock private InterestRepository interestRepository;

  private TravelPlanService service;
  private User owner;

  @BeforeEach
  void setUp() {
    service =
        new TravelPlanService(
            planRepository,
            planInterestRepository,
            itineraryRepository,
            userRepository,
            categoryRepository,
            interestRepository);
    owner = new User();
    owner.setUserId(OWNER_ID);
  }

  // ------------------------------------------------------------ dữ liệu mẫu

  private TravelPlan plan(String status) {
    TravelPlan plan = new TravelPlan();
    plan.setPlanId(PLAN_ID);
    plan.setUser(owner);
    plan.setTitle("Đà Lạt cuối năm");
    plan.setDestination("Đà Lạt");
    plan.setStartDate(START);
    plan.setEndDate(END);
    plan.setStatus(status);
    plan.setCategoryCodes(new ArrayList<>());
    return plan;
  }

  private void givenOwnedPlan(TravelPlan plan) {
    when(planRepository.findByPlanIdAndUserUserId(PLAN_ID, OWNER_ID))
        .thenReturn(Optional.of(plan));
  }

  private static Interest interest(long id, String code) {
    Interest interest = new Interest();
    interest.setInterestId(id);
    interest.setCode(code);
    interest.setName(code);
    return interest;
  }

  private static TravelPlanDtos.CreateRequest createRequest(List<String> interestCodes) {
    return new TravelPlanDtos.CreateRequest(
        "  Đà Lạt cuối năm  ",
        " Đà Lạt ",
        START,
        END,
        new BigDecimal("5000000"),
        "vnd",
        2,
        "Đi cùng bạn",
        null,
        interestCodes,
        null);
  }

  private static TravelPlanDtos.UpdateRequest updateRequest(
      String destination, LocalDate start, LocalDate end, List<String> interestCodes) {
    return new TravelPlanDtos.UpdateRequest(
        "Tên mới", destination, start, end, null, null, null, null, null, interestCodes, "PACKED");
  }

  private static void assertApiError(Throwable thrown, HttpStatus status, String message) {
    assertThat(thrown).isInstanceOf(ApiException.class).hasMessageContaining(message);
    assertThat(((ApiException) thrown).getStatus()).isEqualTo(status);
  }

  // ------------------------------------------------------------ tạo

  @Nested
  @DisplayName("Tạo kế hoạch")
  class Create {

    @Test
    @DisplayName("Lưu kế hoạch DRAFT và các sở thích vào plan_interests")
    void createsDraftWithInterests() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(interestRepository.findByCodeIn(List.of("food", "nature")))
          .thenReturn(List.of(interest(2, "nature"), interest(1, "food")));
      when(planRepository.saveAndFlush(any()))
          .thenAnswer(
              inv -> {
                TravelPlan p = inv.getArgument(0);
                p.setPlanId(PLAN_ID);
                return p;
              });

      TravelPlanDtos.PlanResponse response =
          service.create(OWNER_ID, createRequest(List.of("food", "nature")));

      assertThat(response.status()).isEqualTo("DRAFT");
      assertThat(response.title()).isEqualTo("Đà Lạt cuối năm");
      assertThat(response.destination()).isEqualTo("Đà Lạt");
      assertThat(response.currency()).isEqualTo("VND");
      assertThat(response.travelPace()).isEqualTo("BALANCED");
      assertThat(response.interestCodes()).containsExactly("food", "nature");
      assertThat(response.allowedNextStatuses()).containsExactly("PLANNED", "CANCELLED");

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<PlanInterest>> rows = ArgumentCaptor.forClass(List.class);
      verify(planInterestRepository).saveAll(rows.capture());
      assertThat(rows.getValue())
          .extracting(pi -> pi.getInterest().getCode())
          .containsExactly("food", "nature");
      assertThat(rows.getValue()).allMatch(pi -> pi.getPlan().getPlanId().equals(PLAN_ID));
    }

    @Test
    @DisplayName("Sở thích không tồn tại thì báo lỗi và không lưu gì")
    void rejectsUnknownInterest() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(interestRepository.findByCodeIn(any())).thenReturn(List.of(interest(1, "food")));

      Throwable thrown =
          catchThrowable(
              () -> service.create(OWNER_ID, createRequest(List.of("food", "ghost"))));

      assertApiError(thrown, HttpStatus.BAD_REQUEST, "Sở thích không hợp lệ: ghost");
      verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Sở thích bị trùng thì báo lỗi")
    void rejectsDuplicateInterest() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

      Throwable thrown =
          catchThrowable(
              () -> service.create(OWNER_ID, createRequest(List.of("food", "food"))));

      assertApiError(thrown, HttpStatus.BAD_REQUEST, "Sở thích bị trùng: food");
      verifyNoInteractions(interestRepository);
    }

    @Test
    @DisplayName("Ngày về trước ngày đi thì báo lỗi")
    void rejectsEndBeforeStart() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      TravelPlanDtos.CreateRequest bad =
          new TravelPlanDtos.CreateRequest(
              "A", "B", END, START, null, null, null, null, null, null, null);

      Throwable thrown =
          catchThrowable(() -> service.create(OWNER_ID, bad));

      assertApiError(thrown, HttpStatus.BAD_REQUEST, TravelPlanService.INVALID_DATES);
    }
  }

  // ------------------------------------------------------------ xem

  @Nested
  @DisplayName("Xem kế hoạch")
  class Read {

    @Test
    @DisplayName("Kế hoạch của người khác trả 404")
    void otherUsersPlanIsNotFound() {
      when(planRepository.findByPlanIdAndUserUserId(PLAN_ID, OTHER_ID))
          .thenReturn(Optional.empty());

      Throwable thrown =
          catchThrowable(
              () -> service.getById(OTHER_ID, PLAN_ID));

      assertApiError(thrown, HttpStatus.NOT_FOUND, TravelPlanService.PLAN_NOT_FOUND);
    }

    @Test
    @DisplayName("Danh sách lấy sở thích của mọi kế hoạch trong một truy vấn")
    void listLoadsInterestsOnce() {
      TravelPlan first = plan("DRAFT");
      TravelPlan second = plan("PLANNED");
      second.setPlanId(6L);
      when(planRepository.findByUserUserIdOrderByCreatedAtDesc(OWNER_ID))
          .thenReturn(List.of(first, second));
      PlanInterest row = new PlanInterest();
      row.setPlan(second);
      row.setInterest(interest(1, "food"));
      when(planInterestRepository.findWithInterestByPlanIds(List.of(PLAN_ID, 6L)))
          .thenReturn(List.of(row));

      List<TravelPlanDtos.PlanResponse> plans = service.getMyPlans(OWNER_ID);

      assertThat(plans).hasSize(2);
      assertThat(plans.get(0).interestCodes()).isEmpty();
      assertThat(plans.get(1).interestCodes()).containsExactly("food");
      verify(planInterestRepository).findWithInterestByPlanIds(anyCollection());
    }

    @Test
    @DisplayName("Không có kế hoạch thì không truy vấn sở thích")
    void emptyList() {
      when(planRepository.findByUserUserIdOrderByCreatedAtDesc(OWNER_ID)).thenReturn(List.of());

      assertThat(service.getMyPlans(OWNER_ID)).isEmpty();
      verifyNoInteractions(planInterestRepository);
    }
  }

  // ------------------------------------------------------------ sửa

  @Nested
  @DisplayName("Sửa kế hoạch")
  class Update {

    @Test
    @DisplayName("Sửa được khi PLANNED và thay toàn bộ sở thích")
    void updatesAndReplacesInterests() {
      givenOwnedPlan(plan("PLANNED"));
      when(interestRepository.findByCodeIn(List.of("culture")))
          .thenReturn(List.of(interest(3, "culture")));
      when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

      TravelPlanDtos.PlanResponse response =
          service.update(
              OWNER_ID, PLAN_ID, updateRequest("Đà Lạt", START, END, List.of("culture")));

      assertThat(response.title()).isEqualTo("Tên mới");
      assertThat(response.travelPace()).isEqualTo("PACKED");
      assertThat(response.status()).isEqualTo("PLANNED");
      assertThat(response.interestCodes()).containsExactly("culture");
      InOrder order = inOrder(planInterestRepository);
      order.verify(planInterestRepository).deleteByPlanId(PLAN_ID);
      order.verify(planInterestRepository).saveAll(any());
    }

    @Test
    @DisplayName("Không sửa được khi chuyến đi đã bắt đầu, kết thúc hoặc bị hủy")
    void rejectsWhenNotEditable() {
      for (String status : List.of("IN_PROGRESS", "COMPLETED", "CANCELLED")) {
        givenOwnedPlan(plan(status));

        Throwable thrown =
            catchThrowable(
                () -> service.update(OWNER_ID, PLAN_ID, updateRequest("Đà Lạt", START, END, null)));

        assertApiError(thrown, HttpStatus.CONFLICT, TravelPlanService.NOT_EDITABLE);
      }
      verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Đã có lịch trình thì không cho đổi ngày")
    void rejectsDateChangeWhenItineraryExists() {
      givenOwnedPlan(plan("PLANNED"));
      when(itineraryRepository.existsByPlanPlanId(PLAN_ID)).thenReturn(true);

      Throwable thrown =
          catchThrowable(
              () ->
                  service.update(
                      OWNER_ID, PLAN_ID, updateRequest("Đà Lạt", START, END.plusDays(1), null)));

      assertApiError(thrown, HttpStatus.CONFLICT, TravelPlanService.ITINERARY_LOCKS_TRIP);
      verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Đã có lịch trình vẫn sửa được các trường khác")
    void allowsOtherFieldsWhenItineraryExists() {
      givenOwnedPlan(plan("PLANNED"));
      when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

      // Điểm đến có khoảng trắng thừa vẫn coi là không đổi
      service.update(OWNER_ID, PLAN_ID, updateRequest(" Đà Lạt ", START, END, null));

      verify(itineraryRepository, never()).existsByPlanPlanId(anyLong());
      verify(planRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("Người khác không sửa được")
    void otherUserCannotUpdate() {
      when(planRepository.findByPlanIdAndUserUserId(PLAN_ID, OTHER_ID))
          .thenReturn(Optional.empty());

      Throwable thrown =
          catchThrowable(
              () -> service.update(OTHER_ID, PLAN_ID, updateRequest("X", START, END, null)));

      assertApiError(thrown, HttpStatus.NOT_FOUND, TravelPlanService.PLAN_NOT_FOUND);
    }
  }

  // ------------------------------------------------------------ xóa

  @Nested
  @DisplayName("Xóa kế hoạch")
  class Delete {

    @Test
    @DisplayName("Xóa dữ liệu con theo thứ tự rồi mới xóa kế hoạch")
    void deletesChildrenFirst() {
      givenOwnedPlan(plan("PLANNED"));

      service.delete(OWNER_ID, PLAN_ID);

      InOrder order = inOrder(planRepository, planInterestRepository);
      order.verify(planRepository).deleteActivityProgressByPlanId(PLAN_ID);
      order.verify(planRepository).deleteTripProgressByPlanId(PLAN_ID);
      order.verify(planRepository).deleteItinerarySharesByPlanId(PLAN_ID);
      order.verify(planRepository).deleteItineraryItemsByPlanId(PLAN_ID);
      order.verify(planRepository).deleteItineraryDaysByPlanId(PLAN_ID);
      order.verify(planRepository).deleteItinerariesByPlanId(PLAN_ID);
      order.verify(planRepository).deletePlanPlacesByPlanId(PLAN_ID);
      order.verify(planInterestRepository).deleteByPlanId(PLAN_ID);
      order.verify(planRepository).deleteTripFeedbackByPlanId(PLAN_ID);
      order.verify(planRepository).deletePlanById(PLAN_ID);
    }

    @Test
    @DisplayName("Không xóa khi chuyến đi đang diễn ra")
    void rejectsInProgress() {
      givenOwnedPlan(plan("IN_PROGRESS"));

      Throwable thrown =
          catchThrowable(() -> service.delete(OWNER_ID, PLAN_ID));

      assertApiError(thrown, HttpStatus.CONFLICT, TravelPlanService.NOT_DELETABLE);
      verify(planRepository, never()).deletePlanById(anyLong());
    }

    @Test
    @DisplayName("Người khác không xóa được")
    void otherUserCannotDelete() {
      when(planRepository.findByPlanIdAndUserUserId(PLAN_ID, OTHER_ID))
          .thenReturn(Optional.empty());

      Throwable thrown =
          catchThrowable(() -> service.delete(OTHER_ID, PLAN_ID));

      assertApiError(thrown, HttpStatus.NOT_FOUND, TravelPlanService.PLAN_NOT_FOUND);
      verify(planRepository, never()).deletePlanById(anyLong());
    }
  }

  // ------------------------------------------------------------ đổi trạng thái

  @Nested
  @DisplayName("Đổi trạng thái")
  class ChangeStatus {

    @Test
    @DisplayName("Đi đúng thứ tự DRAFT -> PLANNED")
    void movesForward() {
      TravelPlan draft = plan("DRAFT");
      givenOwnedPlan(draft);
      when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

      TravelPlanDtos.PlanResponse response = service.changeStatus(OWNER_ID, PLAN_ID, "PLANNED");

      assertThat(draft.getStatus()).isEqualTo("PLANNED");
      assertThat(response.status()).isEqualTo("PLANNED");
      assertThat(response.allowedNextStatuses()).containsExactly("IN_PROGRESS", "CANCELLED");
    }

    @Test
    @DisplayName("Hủy được khi đang diễn ra")
    void cancelsInProgress() {
      givenOwnedPlan(plan("IN_PROGRESS"));
      when(planRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

      assertThat(service.changeStatus(OWNER_ID, PLAN_ID, "CANCELLED").allowedNextStatuses())
          .isEmpty();
    }

    @Test
    @DisplayName("Không cho đi ngược")
    void rejectsBackwards() {
      givenOwnedPlan(plan("IN_PROGRESS"));

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OWNER_ID, PLAN_ID, "PLANNED"));

      assertApiError(
          thrown,
          HttpStatus.CONFLICT,
          "Không thể chuyển kế hoạch từ Đang diễn ra sang Đã lên kế hoạch");
      verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Không cho nhảy cóc")
    void rejectsSkipping() {
      givenOwnedPlan(plan("DRAFT"));

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OWNER_ID, PLAN_ID, "COMPLETED"));

      assertApiError(thrown, HttpStatus.CONFLICT, "Chỉ được chuyển sang: Đã lên kế hoạch, Đã hủy");
    }

    @Test
    @DisplayName("Không hủy được kế hoạch đã hoàn thành")
    void rejectsCancelAfterCompleted() {
      givenOwnedPlan(plan("COMPLETED"));

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OWNER_ID, PLAN_ID, "CANCELLED"));

      assertApiError(thrown, HttpStatus.CONFLICT, "không thể đổi trạng thái nữa");
    }

    @Test
    @DisplayName("Chuyển sang chính nó thì báo đã ở trạng thái đó")
    void rejectsSameStatus() {
      givenOwnedPlan(plan("PLANNED"));

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OWNER_ID, PLAN_ID, "PLANNED"));

      assertApiError(thrown, HttpStatus.CONFLICT, "Kế hoạch đã ở trạng thái Đã lên kế hoạch");
    }

    @Test
    @DisplayName("Trạng thái lạ trả 400")
    void rejectsUnknownStatus() {
      givenOwnedPlan(plan("DRAFT"));

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OWNER_ID, PLAN_ID, "ARCHIVED"));

      assertApiError(thrown, HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ");
    }

    @Test
    @DisplayName("Người khác không đổi được")
    void otherUserCannotChange() {
      when(planRepository.findByPlanIdAndUserUserId(PLAN_ID, OTHER_ID))
          .thenReturn(Optional.empty());

      Throwable thrown =
          catchThrowable(
              () -> service.changeStatus(OTHER_ID, PLAN_ID, "PLANNED"));

      assertApiError(thrown, HttpStatus.NOT_FOUND, TravelPlanService.PLAN_NOT_FOUND);
    }
  }
}
