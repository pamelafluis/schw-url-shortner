package com.schw.urlshortener.link.persistence;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Durable storage for ShortLink aggregates, keyed by ShortCode. The unique index backing this port
 * (see the Flyway migration) is the authority ADR-0001's collision-retry guarantee depends on.
 *
 * <p>Generated and Alias codes are saved through separate methods because they disagree on what a
 * collision means: a generated-code collision is silently retried with a fresh code, while an Alias
 * collision is the caller's explicit choice and must be reported, never substituted.
 */
public interface ShortLinkRepository {

  ShortLink saveGenerated(
      TargetUrl targetUrl, String createdBy, Instant createdAt, Optional<Instant> expiresAt);

  ShortLink saveWithAlias(
      ShortCode alias,
      TargetUrl targetUrl,
      String createdBy,
      Instant createdAt,
      Optional<Instant> expiresAt);

  Optional<ShortLink> findByCode(ShortCode code);

  void deactivate(ShortCode code);

  /**
   * Applies each delta as {@code click_count = click_count + delta}, never an overwrite, so
   * concurrent flushes (and any future multi-instance deployment) can't lose an update to a race.
   * See ADR-0003.
   */
  void recordClicks(Map<ShortCode, Long> deltas);
}
