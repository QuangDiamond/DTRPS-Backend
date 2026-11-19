package com.dtrps.modules.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dtrps.exception.ApiException;
import com.dtrps.modules.notification.dto.LiveEvent;
import com.dtrps.modules.notification.dto.Notice;
import com.dtrps.modules.notification.dto.NotificationCategory;
import com.dtrps.modules.notification.dto.NotificationPage;
import com.dtrps.modules.notification.dto.NotificationResponse;
import com.dtrps.modules.user.entity.Notification;
import com.dtrps.modules.user.entity.User;
import com.dtrps.modules.user.repository.NotificationRepository;
import com.dtrps.modules.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unit test NotificationService — sheet Notification, UTC001–UTC013 trong Report5.1. Giao dịch được
 * mô phỏng bằng TransactionSynchronizationManager: commit = chạy afterCommit, rollback = bỏ qua.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-19T03:00:00Z");
  private static final Long USER_ID = 7L;
  private static final Notice NOTICE =
      new Notice(
          "REVIEW_HIDDEN", "Đánh giá của bạn đã bị ẩn", "Lý do: spam", "/explore/1", Map.of());

  @Mock private NotificationRepository notifications;
  @Mock private UserRepository users;
  @Mock private LiveEventHub hub;

  private NotificationService service;
  private final AtomicLong ids = new AtomicLong(100);
  private final List<Notification> saved = new ArrayList<>();

  @BeforeEach
  void setUp() {
    service = new NotificationService(notifications, users, hub, Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(users.getReferenceById(any()))
        .thenAnswer(
            call -> {
              User user = new User();
              user.setUserId(call.getArgument(0));
              return user;
            });
    lenient()
        .when(notifications.save(any(Notification.class)))
        .thenAnswer(
            call -> {
              Notification notification = call.getArgument(0);
              notification.setNotificationId(ids.incrementAndGet());
              saved.add(notification);
              return notification;
            });
  }

  @AfterEach
  void endTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static void beginTransaction() {
    TransactionSynchronizationManager.initSynchronization();
  }

  private static void commit() {
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    TransactionSynchronizationManager.clearSynchronization();
  }

  private static void rollback() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private static Notification row(long id, String type) {
    Notification notification = new Notification();
    notification.setNotificationId(id);
    notification.setType(type);
    notification.setTitle("t");
    notification.setIsRead(false);
    return notification;
  }

  @Nested
  @DisplayName("notifyUser")
  class NotifyUser {
    @Test
    @DisplayName("UTC001 Inside a transaction → saved now, pushed only after commit")
    void pushedAfterCommit() {
      beginTransaction();

      service.notifyUser(USER_ID, NOTICE);

      assertThat(saved).hasSize(1);
      assertThat(saved.get(0).getType()).isEqualTo("REVIEW_HIDDEN");
      assertThat(saved.get(0).getSentAt()).isEqualTo(NOW);
      verifyNoInteractions(hub);

      commit();

      ArgumentCaptor<LiveEvent> event = ArgumentCaptor.forClass(LiveEvent.class);
      verify(hub).sendToUser(eq(USER_ID), event.capture());
      assertThat(event.getValue().notificationId()).isEqualTo(saved.get(0).getNotificationId());
      assertThat(event.getValue().category()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("UTC002 Transaction rolled back → nothing pushed")
    void rollback_notPushed() {
      beginTransaction();

      service.notifyUser(USER_ID, NOTICE);
      rollback();

      verifyNoInteractions(hub);
    }
  }

  @Nested
  @DisplayName("notifyAdmins")
  class NotifyAdmins {
    @Test
    @DisplayName("UTC003 3 admins, admin 1 acted → 2 saved, admin 1 only gets a silent refresh")
    void actorExcluded() {
      when(users.findActiveIdsByRole("ADMIN")).thenReturn(List.of(1L, 2L, 3L));

      service.notifyAdmins(NOTICE, 1L);

      assertThat(saved).extracting(item -> item.getUser().getUserId()).containsExactly(2L, 3L);
      ArgumentCaptor<LiveEvent> event = ArgumentCaptor.forClass(LiveEvent.class);
      verify(hub).sendToUser(eq(1L), event.capture());
      assertThat(event.getValue().notificationId()).isNull();
      verify(hub).sendToUser(eq(2L), any());
      verify(hub).sendToUser(eq(3L), any());
    }

    @Test
    @DisplayName("UTC004 No active admin → nothing saved, no error")
    void noAdmins_nothing() {
      service.notifyAdmins(NOTICE, null);

      assertThat(saved).isEmpty();
      verifyNoInteractions(hub);
    }

    @Test
    @DisplayName("UTC005 3 admins, no actor (system event) → 3 saved")
    void noActor_allAdmins() {
      when(users.findActiveIdsByRole("ADMIN")).thenReturn(List.of(1L, 2L, 3L));

      service.notifyAdmins(NOTICE, null);

      assertThat(saved).hasSize(3);
      verify(hub, times(3)).sendToUser(anyLong(), any());
    }
  }

  @Nested
  @DisplayName("list")
  class ListTests {
    @Test
    @DisplayName("UTC006 Tab ADS → only AD_* types queried, unread counted per tab")
    void adsTab_onlyAdTypes() {
      List<Object[]> types = new ArrayList<>();
      types.add(new Object[] {"AD_PAYMENT_SUCCESS", 2L});
      types.add(new Object[] {"LISTING_APPROVED", 1L});
      types.add(new Object[] {"AD_SUSPENDED", 0L});
      when(notifications.countTypes(USER_ID)).thenReturn(types);
      PageRequest request = PageRequest.of(0, 20);
      when(notifications.findByUserUserIdAndTypeInOrderByCreatedAtDescNotificationIdDesc(
              USER_ID, List.of("AD_PAYMENT_SUCCESS", "AD_SUSPENDED"), request))
          .thenReturn(new PageImpl<>(List.of(row(9, "AD_PAYMENT_SUCCESS")), request, 1));
      when(notifications.countByUserUserIdAndIsReadFalse(USER_ID)).thenReturn(3L);

      NotificationPage page = service.list(USER_ID, false, NotificationCategory.ADS, 0, 20);

      assertThat(page.items())
          .extracting(NotificationResponse::type)
          .containsExactly("AD_PAYMENT_SUCCESS");
      assertThat(page.unread()).isEqualTo(3);
      assertThat(page.categories()).containsEntry("ADS", 2L).containsEntry("LISTING", 1L);
    }

    @Test
    @DisplayName("UTC007 category=\"ABC\" → not a known tab (controller answers 400)")
    void unknownCategory_rejected() {
      assertThat(NotificationCategory.parse("ABC")).isEmpty();
      assertThat(NotificationCategory.parse(" ads ")).contains(NotificationCategory.ADS);
    }

    @Test
    @DisplayName("UTC008 Tab REVIEW without any review notification → empty page, no query")
    void emptyTab_emptyPage() {
      List<Object[]> types = new ArrayList<>();
      types.add(new Object[] {"AD_PAYMENT_SUCCESS", 1L});
      when(notifications.countTypes(USER_ID)).thenReturn(types);

      NotificationPage page = service.list(USER_ID, false, NotificationCategory.REVIEW, 0, 20);

      assertThat(page.items()).isEmpty();
      assertThat(page.total()).isZero();
      verify(notifications, never())
          .findByUserUserIdAndTypeInOrderByCreatedAtDescNotificationIdDesc(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("markRead")
  class MarkRead {
    @Test
    @DisplayName("UTC009 Own unread notification → read = true")
    void own_marked() {
      Notification mine = row(9, "AD_SUSPENDED");
      when(notifications.findByNotificationIdAndUserUserId(9L, USER_ID))
          .thenReturn(Optional.of(mine));

      NotificationResponse response = service.markRead(USER_ID, 9L);

      assertThat(mine.getIsRead()).isTrue();
      assertThat(response.read()).isTrue();
    }

    @Test
    @DisplayName("UTC010 Notification of another user → 404")
    void otherUser_notFound() {
      ApiException e =
          catchThrowableOfType(() -> service.markRead(USER_ID, 9L), ApiException.class);

      assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(e.getMessage()).isEqualTo("Không tìm thấy thông báo");
    }
  }

  @Nested
  @DisplayName("markAllRead")
  class MarkAllRead {
    @Test
    @DisplayName("UTC011 5 unread → returns 5")
    void five_marked() {
      when(notifications.markAllRead(USER_ID)).thenReturn(5);

      assertThat(service.markAllRead(USER_ID)).isEqualTo(5);
    }

    @Test
    @DisplayName("UTC012 Called by user B → only B's notifications are touched")
    void onlyCaller() {
      service.markAllRead(8L);

      verify(notifications).markAllRead(8L);
      verify(notifications, never()).markAllRead(USER_ID);
    }

    @Test
    @DisplayName("UTC013 Nothing unread → returns 0")
    void none_zero() {
      assertThat(service.markAllRead(USER_ID)).isZero();
    }
  }
}
