package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.InvalidTargetUrlException;
import com.schw.urlshortener.link.domain.MalformedAliasException;
import com.schw.urlshortener.link.domain.ReservedAliasException;
import com.schw.urlshortener.link.persistence.AliasConflictException;
import com.schw.urlshortener.link.persistence.ShortCodeGenerationExhaustedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/persistence/API exceptions to RFC 9457 {@code application/problem+json} responses per
 * the README's error table. The 401 case (missing/unknown API key) is handled in {@link
 * ApiKeyFilter} instead, since it never reaches the DispatcherServlet this advice sits in front of.
 */
@RestControllerAdvice
class ProblemDetailExceptionHandler {

  @ExceptionHandler(InvalidTargetUrlException.class)
  ResponseEntity<ProblemDetail> handleInvalidTargetUrl(
      InvalidTargetUrlException e, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "invalid-target-url", "Invalid TargetUrl", e.getMessage(), request);
  }

  @ExceptionHandler(MalformedAliasException.class)
  ResponseEntity<ProblemDetail> handleMalformedAlias(
      MalformedAliasException e, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "malformed-alias", "Malformed alias", e.getMessage(), request);
  }

  @ExceptionHandler(ReservedAliasException.class)
  ResponseEntity<ProblemDetail> handleReservedAlias(
      ReservedAliasException e, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT, "reserved-alias", "Alias is reserved", e.getMessage(), request);
  }

  @ExceptionHandler(AliasConflictException.class)
  ResponseEntity<ProblemDetail> handleAliasConflict(
      AliasConflictException e, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT, "alias-taken", "Alias already in use", e.getMessage(), request);
  }

  @ExceptionHandler(ShortLinkNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(
      ShortLinkNotFoundException e, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND,
        "short-link-not-found",
        "ShortLink not found",
        e.getMessage(),
        request);
  }

  @ExceptionHandler(NotOwnerException.class)
  ResponseEntity<ProblemDetail> handleNotOwner(NotOwnerException e, HttpServletRequest request) {
    return problem(HttpStatus.FORBIDDEN, "not-owner", "Not the owner", e.getMessage(), request);
  }

  @ExceptionHandler(ShortLinkGoneException.class)
  ResponseEntity<ProblemDetail> handleGone(ShortLinkGoneException e, HttpServletRequest request) {
    return problem(
        HttpStatus.GONE, "short-link-gone", "ShortLink is gone", e.getMessage(), request);
  }

  @ExceptionHandler(ShortCodeGenerationExhaustedException.class)
  ResponseEntity<ProblemDetail> handleGenerationExhausted(
      ShortCodeGenerationExhaustedException e, HttpServletRequest request) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "short-code-generation-exhausted",
        "ShortCode generation exhausted",
        e.getMessage(),
        request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> handleMalformedRequest(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "malformed-request",
        "Malformed request body",
        "The request body is malformed or missing.",
        request);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status,
      String typeSuffix,
      String title,
      String detail,
      HttpServletRequest request) {
    ProblemDetail body = Problems.of(status, typeSuffix, title, detail, request.getRequestURI());
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }
}
