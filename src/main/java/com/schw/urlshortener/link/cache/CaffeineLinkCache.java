package com.schw.urlshortener.link.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Caffeine-backed {@link LinkCache}. Bounded to {@link #MAXIMUM_SIZE} entries; each entry expires
 * at {@code min(MAXIMUM_TTL, time until that ShortLink's expiresAt)} — negative-cache entries,
 * having no expiresAt, always use the maximum. See ADR-0004.
 */
public final class CaffeineLinkCache implements LinkCache {

  static final Duration MAXIMUM_TTL = Duration.ofSeconds(60);
  static final long MAXIMUM_SIZE = 100_000;

  private final Cache<ShortCode, Optional<ShortLink>> cache;
  private final Clock clock;

  public CaffeineLinkCache(Clock clock) {
    this(clock, Ticker.systemTicker(), MAXIMUM_SIZE);
  }

  // Test-only seam: exposes the Caffeine Ticker and bound so tests can drive expiry and
  // eviction deterministically without waiting on real time or inserting 100k entries.
  CaffeineLinkCache(Clock clock, Ticker ticker, long maximumSize) {
    this.clock = clock;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .ticker(ticker)
            .executor(Runnable::run)
            .expireAfter(new PerEntryExpiry())
            .build();
  }

  @Override
  public Optional<Optional<ShortLink>> get(ShortCode code) {
    return Optional.ofNullable(cache.getIfPresent(code));
  }

  @Override
  public void put(ShortCode code, Optional<ShortLink> value) {
    cache.put(code, value);
  }

  @Override
  public void invalidate(ShortCode code) {
    cache.invalidate(code);
  }

  void cleanUp() {
    cache.cleanUp();
  }

  long estimatedSize() {
    return cache.estimatedSize();
  }

  private final class PerEntryExpiry implements Expiry<ShortCode, Optional<ShortLink>> {

    @Override
    public long expireAfterCreate(ShortCode key, Optional<ShortLink> value, long currentTime) {
      return ttl(value).toNanos();
    }

    @Override
    public long expireAfterUpdate(
        ShortCode key, Optional<ShortLink> value, long currentTime, long currentDuration) {
      return ttl(value).toNanos();
    }

    @Override
    public long expireAfterRead(
        ShortCode key, Optional<ShortLink> value, long currentTime, long currentDuration) {
      return currentDuration;
    }

    private Duration ttl(Optional<ShortLink> value) {
      Optional<Instant> expiresAt = value.flatMap(ShortLink::expiresAt);
      if (expiresAt.isEmpty()) {
        return MAXIMUM_TTL;
      }
      Duration untilExpiry = Duration.between(clock.instant(), expiresAt.get());
      if (untilExpiry.isNegative()) {
        return Duration.ZERO;
      }
      return untilExpiry.compareTo(MAXIMUM_TTL) < 0 ? untilExpiry : MAXIMUM_TTL;
    }
  }
}
