package com.dtrps.security;

import com.dtrps.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Per-IP fixed window, per instance. Behind a proxy the proxy must pass the client address. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private static final Duration WINDOW = Duration.ofMinutes(1);
  private static final long MAX_TRACKED_CLIENTS = 100_000;
  private static final String TOO_MANY_REQUESTS = "Bạn thao tác quá nhanh, vui lòng thử lại sau";

  private final Cache<String, AtomicInteger> counters =
      Caffeine.newBuilder().expireAfterWrite(WINDOW).maximumSize(MAX_TRACKED_CLIENTS).build();
  private final int limitPerMinute;
  private final ObjectMapper objectMapper;

  public AuthRateLimitFilter(int limitPerMinute, ObjectMapper objectMapper) {
    this.limitPerMinute = limitPerMinute;
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !SecurityConfig.isPublicAuthRequest(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = request.getRemoteAddr() + '|' + SecurityConfig.decodedPath(request);
    int attempts = counters.get(key, unused -> new AtomicInteger()).incrementAndGet();
    if (attempts <= limitPerMinute) {
      chain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.toSeconds()));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(TOO_MANY_REQUESTS));
  }
}
