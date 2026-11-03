package com.dtrps.modules.auth.service;

import com.dtrps.security.AuthProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Stops password guessing against one account from many IPs. Counts per app instance. */
@Component
public class LoginThrottle {
  private static final long MAX_TRACKED_ACCOUNTS = 100_000;

  private final Cache<String, AtomicInteger> failures;
  private final int maxFailedLogins;

  public LoginThrottle(AuthProperties properties) {
    this.maxFailedLogins = properties.maxFailedLogins();
    this.failures =
        Caffeine.newBuilder()
            .expireAfterWrite(properties.loginLockDuration())
            .maximumSize(MAX_TRACKED_ACCOUNTS)
            .build();
  }

  public boolean isBlocked(String email) {
    AtomicInteger count = failures.getIfPresent(email);
    return count != null && count.get() >= maxFailedLogins;
  }

  public void recordFailure(String email) {
    failures.get(email, unused -> new AtomicInteger()).incrementAndGet();
  }

  public void reset(String email) {
    failures.invalidate(email);
  }
}
