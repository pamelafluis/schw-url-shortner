package com.schw.urlshortener.link.domain;

import java.time.Instant;
import java.util.Optional;

public final class ShortLink {

  private final ShortCode code;
  private final TargetUrl targetUrl;
  private final String createdBy;
  private final Instant createdAt;
  private final Optional<Instant> expiresAt;
  private final long clickCount;
  private boolean active;

  public ShortLink(
      ShortCode code,
      TargetUrl targetUrl,
      String createdBy,
      Instant createdAt,
      Optional<Instant> expiresAt) {
    this(code, targetUrl, createdBy, createdAt, expiresAt, 0L);
  }

  /** Reconstitution from persistence, where clickCount is whatever was last flushed to it. */
  public ShortLink(
      ShortCode code,
      TargetUrl targetUrl,
      String createdBy,
      Instant createdAt,
      Optional<Instant> expiresAt,
      long clickCount) {
    this.code = code;
    this.targetUrl = targetUrl;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.clickCount = clickCount;
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }

  // Deactivation is checked before expiry so a ShortLink that is both expired and
  // deactivated is reported as deactivated — both converge to 410 at the API layer,
  // but stay distinguishable here for logging (issue #4).
  public ResolutionOutcome resolutionOutcome(Instant now) {
    if (!active) {
      return ResolutionOutcome.DEACTIVATED;
    }
    if (expiresAt.isPresent() && now.isAfter(expiresAt.get())) {
      return ResolutionOutcome.EXPIRED;
    }
    return ResolutionOutcome.RESOLVABLE;
  }

  public ShortCode code() {
    return code;
  }

  public TargetUrl targetUrl() {
    return targetUrl;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> expiresAt() {
    return expiresAt;
  }

  public boolean active() {
    return active;
  }

  public long clickCount() {
    return clickCount;
  }
}
