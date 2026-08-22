package com.schw.urlshortener.link.cache;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import java.util.Optional;

/**
 * A bounded, TTL'd cache sitting in front of {@link
 * com.schw.urlshortener.link.persistence.ShortLinkRepository} on the resolve path only, keyed by
 * {@link ShortCode}. See ADR-0004.
 */
public interface LinkCache {

  /**
   * Looks up a cached lookup result. The outer {@link Optional} is empty when {@code code} is not
   * currently cached — never cached, or evicted by TTL/size/invalidation — meaning the caller must
   * consult Postgres and then {@link #put}. When the outer Optional is present, an empty inner
   * Optional is a negative-cache entry (a ShortCode already confirmed not to exist); a present
   * inner Optional is a cache hit.
   */
  // TODO(#15): replace this nested Optional with a small sum type (Uncached/Hit/NegativeHit)
  // so the three states are self-evident at call sites instead of relying on this javadoc.
  Optional<Optional<ShortLink>> get(ShortCode code);

  /**
   * Caches a lookup result for {@code code}. An empty {@code value} populates a negative-cache
   * entry; a present value populates a hit. TTL is computed per-entry from the ShortLink's
   * expiresAt, clamped to a maximum (see ADR-0004) — negative-cache entries always use that
   * maximum, since there is no expiresAt to clamp against.
   */
  void put(ShortCode code, Optional<ShortLink> value);

  /**
   * Evicts any cached entry for {@code code}, a no-op if none exists. Deactivation calls this so
   * the local instance stops resolving the code immediately rather than waiting out its TTL — an
   * improvement on top of the published 60s SLO, not a replacement for it: other instances in a
   * fleet still only see the deactivation once their own TTL elapses (see ADR-0004).
   */
  void invalidate(ShortCode code);
}
