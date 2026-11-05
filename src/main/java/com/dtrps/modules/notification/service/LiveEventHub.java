package com.dtrps.modules.notification.service;

import com.dtrps.modules.notification.dto.LiveEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open event streams of signed-in browsers (one per tab), kept in memory. Fine for a single backend
 * instance; several instances would need a shared broker (e.g. Redis pub/sub) to fan events out.
 */
@Component
public class LiveEventHub {
  private static final Logger log = LoggerFactory.getLogger(LiveEventHub.class);

  /** The browser reconnects after this; keeps proxies and stale tokens from holding forever */
  static final Duration STREAM_LIFETIME = Duration.ofMinutes(30);

  static final String EVENT_NAME = "notification";

  private record Client(Long userId, boolean admin, SseEmitter emitter) {}

  private final List<Client> clients = new CopyOnWriteArrayList<>();
  private final Map<SseEmitter, Client> byEmitter = new ConcurrentHashMap<>();

  public SseEmitter subscribe(Long userId, boolean admin) {
    SseEmitter emitter = new SseEmitter(STREAM_LIFETIME.toMillis());
    Client client = new Client(userId, admin, emitter);
    clients.add(client);
    byEmitter.put(emitter, client);
    emitter.onCompletion(() -> remove(emitter));
    emitter.onTimeout(() -> remove(emitter));
    emitter.onError(error -> remove(emitter));
    try {
      emitter.send(SseEmitter.event().name("ready").data("ok"));
    } catch (IOException e) {
      remove(emitter);
    }
    return emitter;
  }

  public void sendToUser(Long userId, LiveEvent event) {
    clients.stream().filter(c -> c.userId().equals(userId)).forEach(c -> send(c, event));
  }

  /** Every open admin tab, except those of {@code exceptUserId} (the admin who acted). */
  public void sendToAdmins(LiveEvent event, Long exceptUserId) {
    clients.stream()
        .filter(Client::admin)
        .filter(c -> !c.userId().equals(exceptUserId))
        .forEach(c -> send(c, event));
  }

  /** Comment lines keep idle connections open through proxies and reveal closed tabs. */
  @Scheduled(fixedDelay = 25_000)
  void heartbeat() {
    for (Client client : clients) {
      try {
        client.emitter().send(SseEmitter.event().comment("ping"));
      } catch (IOException | IllegalStateException e) {
        remove(client.emitter());
      }
    }
  }

  int openStreams() {
    return clients.size();
  }

  private void send(Client client, LiveEvent event) {
    try {
      client.emitter().send(SseEmitter.event().name(EVENT_NAME).data(event));
    } catch (IOException | IllegalStateException e) {
      log.debug("Dropping closed event stream of user {}", client.userId());
      remove(client.emitter());
    }
  }

  private void remove(SseEmitter emitter) {
    Client client = byEmitter.remove(emitter);
    if (client != null) {
      clients.remove(client);
    }
  }
}
