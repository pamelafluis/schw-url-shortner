package com.schw.urlshortener.link.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards {@code /api/**} with an {@code X-API-Key} header. The resolve endpoint ({@code GET
 * /{code}}) is deliberately not under {@code /api/**} and is left unauthenticated per the README.
 *
 * <p>The valid key doubles as the caller's identity (see {@link #API_KEY_ATTRIBUTE}) — there are no
 * separate accounts, per the README's "Deliberately not built".
 */
@Component
class ApiKeyFilter extends OncePerRequestFilter {

  static final String API_KEY_ATTRIBUTE = "apiKey";

  private final Set<String> validApiKeys;
  private final ObjectMapper objectMapper;

  ApiKeyFilter(@Value("${app.api-keys}") List<String> validApiKeys, ObjectMapper objectMapper) {
    this.validApiKeys = Set.copyOf(validApiKeys);
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String key = request.getHeader("X-API-Key");
    if (key == null || !validApiKeys.contains(key)) {
      writeUnauthorized(request, response);
      return;
    }
    request.setAttribute(API_KEY_ATTRIBUTE, key);
    filterChain.doFilter(request, response);
  }

  private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(),
        Problems.of(
            HttpStatus.UNAUTHORIZED,
            "missing-or-invalid-api-key",
            "Missing or invalid API key",
            "The 'X-API-Key' header is missing or does not match a configured key.",
            request.getRequestURI()));
  }
}
