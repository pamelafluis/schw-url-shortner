package com.schw.urlshortener.link.api;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 {@code application/problem+json} bodies. Shared by the {@link
 * ProblemDetailExceptionHandler} (exceptions raised inside the DispatcherServlet) and {@link
 * ApiKeyFilter} (401s raised before it, which never reach the advice).
 */
final class Problems {

  private static final String TYPE_BASE = "https://example.com/probs/";

  private Problems() {}

  static ProblemDetail of(
      HttpStatus status, String typeSuffix, String title, String detail, String instance) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(TYPE_BASE + typeSuffix));
    problem.setTitle(title);
    problem.setInstance(URI.create(instance));
    return problem;
  }
}
