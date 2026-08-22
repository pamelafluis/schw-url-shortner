package com.schw.urlshortener.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates every request with an ID: honors an inbound {@code X-Request-Id} if the caller
 * supplied one, otherwise generates one. The ID is put in MDC for the duration of the request, so
 * every log line emitted while handling it (including framework logs) carries it, and echoed back
 * as a response header so a caller can hand it to an operator.
 *
 * <p>Unlike {@link com.schw.urlshortener.link.api.ApiKeyFilter}, this runs for every request,
 * including {@code GET /{code}}. Ordered ahead of everything else so the ID is in MDC (and the
 * response header is set) even for requests {@code ApiKeyFilter} rejects.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Request-Id";
  private static final String MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    response.setHeader(HEADER, requestId);
    MDC.put(MDC_KEY, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
