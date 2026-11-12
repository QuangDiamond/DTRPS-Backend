package com.dtrps.modules.discovery.service;

import com.dtrps.modules.discovery.config.OpenAiProperties;
import com.dtrps.modules.poi.entity.Place;
import com.dtrps.modules.poi.entity.PlaceCategory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AbstractCollectionEvent;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCollectionRecreateEvent;
import org.hibernate.event.spi.PostCollectionRecreateEventListener;
import org.hibernate.event.spi.PostCollectionRemoveEvent;
import org.hibernate.event.spi.PostCollectionRemoveEventListener;
import org.hibernate.event.spi.PostCollectionUpdateEvent;
import org.hibernate.event.spi.PostCollectionUpdateEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tự sinh lại vector khi địa điểm được thêm hoặc sửa — không cần gọi rebuild bằng tay.
 *
 * <p>Bắt sự kiện lưu {@link Place} của Hibernate (mọi nơi: admin, doanh nghiệp, duyệt bản sửa…),
 * đợi transaction commit xong rồi mới nhúng ở một luồng nền riêng, nên không làm chậm hay làm hỏng
 * thao tác lưu. Nội dung không đổi (cùng hash) thì không gọi OpenAI. Đổi tên loại hình thì nhúng lại
 * toàn bộ (mô tả nhúng có chứa tên loại hình). Khi khởi động cũng quét một lượt để bù các điểm được
 * nhập thẳng bằng SQL.
 */
@Component
public class PlaceEmbeddingAutoSync
    implements PostInsertEventListener,
        PostUpdateEventListener,
        PostCollectionRecreateEventListener,
        PostCollectionUpdateEventListener,
        PostCollectionRemoveEventListener {

  private static final Logger log = LoggerFactory.getLogger(PlaceEmbeddingAutoSync.class);
  private static final int FULL_REBUILD_LIMIT = 500;

  private final EntityManagerFactory entityManagerFactory;
  private final PlaceEmbeddingService embeddingService;
  private final OpenAiProperties properties;

  /** Một luồng duy nhất: các lần nhúng chạy lần lượt, không ghi đè nhau. */
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "place-embedding-sync");
            thread.setDaemon(true);
            return thread;
          });

  private final Set<Long> pendingPlaceIds = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean fullRebuildPending = new AtomicBoolean();

  public PlaceEmbeddingAutoSync(
      EntityManagerFactory entityManagerFactory,
      PlaceEmbeddingService embeddingService,
      OpenAiProperties properties) {
    this.entityManagerFactory = entityManagerFactory;
    this.embeddingService = embeddingService;
    this.properties = properties;
  }

  @PostConstruct
  void registerHibernateListeners() {
    EventListenerRegistry registry =
        entityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getServiceRegistry()
            .getService(EventListenerRegistry.class);

    registry.appendListeners(EventType.POST_INSERT, this);
    registry.appendListeners(EventType.POST_UPDATE, this);
    registry.appendListeners(EventType.POST_COLLECTION_RECREATE, this);
    registry.appendListeners(EventType.POST_COLLECTION_UPDATE, this);
    registry.appendListeners(EventType.POST_COLLECTION_REMOVE, this);
  }

  /** Khởi động xong: bù vector cho các điểm còn thiếu hoặc đã đổi nội dung. */
  @EventListener(ApplicationReadyEvent.class)
  void backfillOnStartup() {
    requestFullRebuild();
  }

  @PreDestroy
  void shutdown() {
    worker.shutdownNow();
  }

  // ------------------------------------------------------------- sự kiện Hibernate

  @Override
  public void onPostInsert(PostInsertEvent event) {
    if (event.getEntity() instanceof Place place) {
      requestPlace(place.getPlaceId());
    }
  }

  @Override
  public void onPostUpdate(PostUpdateEvent event) {
    if (event.getEntity() instanceof Place place) {
      requestPlace(place.getPlaceId());
    } else if (event.getEntity() instanceof PlaceCategory) {
      requestFullRebuild();
    }
  }

  @Override
  public void onPostRecreateCollection(PostCollectionRecreateEvent event) {
    onCollectionChanged(event);
  }

  @Override
  public void onPostUpdateCollection(PostCollectionUpdateEvent event) {
    onCollectionChanged(event);
  }

  @Override
  public void onPostRemoveCollection(PostCollectionRemoveEvent event) {
    onCollectionChanged(event);
  }

  @Override
  public boolean requiresPostCommitHandling(EntityPersister persister) {
    return false;
  }

  /** Loại hình phụ (place_category_links) đổi cũng làm đổi nội dung nhúng. */
  private void onCollectionChanged(AbstractCollectionEvent event) {
    if (event.getAffectedOwnerOrNull() instanceof Place place) {
      requestPlace(place.getPlaceId());
    }
  }

  // ------------------------------------------------------------- hàng đợi

  private void requestPlace(Long placeId) {
    if (placeId != null) {
      afterCommit(() -> pendingPlaceIds.add(placeId));
    }
  }

  private void requestFullRebuild() {
    afterCommit(() -> fullRebuildPending.set(true));
  }

  /** Chỉ nhúng khi dữ liệu đã thực sự lưu; transaction rollback thì bỏ qua. */
  private void afterCommit(Runnable enqueue) {
    Runnable submit =
        () -> {
          enqueue.run();
          worker.submit(this::drain);
        };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              submit.run();
            }
          });
    } else {
      submit.run();
    }
  }

  private void drain() {
    if (!properties.configured()) {
      pendingPlaceIds.clear();
      fullRebuildPending.set(false);
      return;
    }

    try {
      if (fullRebuildPending.getAndSet(false)) {
        pendingPlaceIds.clear();
        PlaceEmbeddingService.RebuildResult result =
            embeddingService.rebuild(FULL_REBUILD_LIMIT);
        log.info(
            "Auto embedding (toàn bộ): embedded={}, skipped={}, failed={}",
            result.embedded(), result.skipped(), result.failed());
        return;
      }

      Set<Long> batch = new HashSet<>();
      for (Iterator<Long> it = pendingPlaceIds.iterator(); it.hasNext(); ) {
        batch.add(it.next());
        it.remove();
      }
      if (batch.isEmpty()) {
        return;
      }

      PlaceEmbeddingService.RebuildResult result = embeddingService.syncPlaces(batch);
      if (result.embedded() > 0 || result.failed() > 0) {
        log.info(
            "Auto embedding places={}: embedded={}, skipped={}, failed={}",
            batch, result.embedded(), result.skipped(), result.failed());
      }
    } catch (RuntimeException ex) {
      // Lỗi OpenAI/mạng không được làm hỏng thao tác lưu địa điểm; lần sửa sau hoặc lần khởi động
      // sau sẽ nhúng lại (hash khác thì chưa được ghi).
      log.warn("Auto embedding thất bại: {}", ex.getMessage());
    }
  }
}
