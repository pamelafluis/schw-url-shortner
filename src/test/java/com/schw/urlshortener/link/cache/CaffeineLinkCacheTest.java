package com.schw.urlshortener.link.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CaffeineLinkCacheTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final TargetUrl TARGET_URL = TargetUrl.reconstitute("https://example.com/x");

  @Test
  void missingCodeIsNotCached() {
    CaffeineLinkCache cache = newCache();

    assertThat(cache.get(ShortCode.reconstitute("aB3xK9z"))).isEmpty();
  }

  @Test
  void cachedHitIsReturnedWithoutConsultingPostgres() {
    CaffeineLinkCache cache = newCache();
    ShortLink shortLink = newShortLink(Optional.empty());

    cache.put(shortLink.code(), Optional.of(shortLink));

    assertThat(cache.get(shortLink.code())).contains(Optional.of(shortLink));
  }

  @Test
  void negativeCacheEntryIsDistinguishableFromNoEntry() {
    CaffeineLinkCache cache = newCache();
    ShortCode neverIssued = ShortCode.reconstitute("nvrissud");

    cache.put(neverIssued, Optional.empty());

    assertThat(cache.get(neverIssued)).contains(Optional.empty());
  }

  @Test
  void hitWithNoExpiryUsesTheMaximumTtl() {
    ManualTime time = new ManualTime(NOW);
    CaffeineLinkCache cache = newCache(time);
    ShortLink shortLink = newShortLink(Optional.empty());
    cache.put(shortLink.code(), Optional.of(shortLink));

    time.advance(CaffeineLinkCache.MAXIMUM_TTL.minusSeconds(1));
    assertThat(cache.get(shortLink.code())).isPresent();

    time.advance(Duration.ofSeconds(2));
    assertThat(cache.get(shortLink.code())).isEmpty();
  }

  @Test
  void ttlIsClampedToTimeUntilExpiryWhenSoonerThanTheMaximum() {
    ManualTime time = new ManualTime(NOW);
    CaffeineLinkCache cache = newCache(time);
    ShortLink shortLink = newShortLink(Optional.of(NOW.plusSeconds(10)));
    cache.put(shortLink.code(), Optional.of(shortLink));

    time.advance(Duration.ofSeconds(9));
    assertThat(cache.get(shortLink.code())).isPresent();

    time.advance(Duration.ofSeconds(2));
    assertThat(cache.get(shortLink.code())).isEmpty();
  }

  @Test
  void negativeCacheEntryExpiresAfterTheMaximumTtl() {
    ManualTime time = new ManualTime(NOW);
    CaffeineLinkCache cache = newCache(time);
    ShortCode neverIssued = ShortCode.reconstitute("nvrissud");
    cache.put(neverIssued, Optional.empty());

    time.advance(CaffeineLinkCache.MAXIMUM_TTL.plusSeconds(1));

    assertThat(cache.get(neverIssued)).isEmpty();
  }

  @Test
  void deactivatedShortLinkStopsResolvingLocallyOnceItsCacheEntryExpires() {
    ManualTime time = new ManualTime(NOW);
    CaffeineLinkCache cache = newCache(time);
    ShortLink shortLink = newShortLink(Optional.empty());
    cache.put(shortLink.code(), Optional.of(shortLink));
    assertThat(cache.get(shortLink.code())).contains(Optional.of(shortLink));

    // No explicit invalidation on deactivate (see ADR-0004) — the cached, still-active
    // snapshot only stops being served once its TTL elapses and it is evicted.
    time.advance(CaffeineLinkCache.MAXIMUM_TTL.plusSeconds(1));

    assertThat(cache.get(shortLink.code())).isEmpty();
  }

  @Test
  void invalidateEvictsAnExistingEntryImmediately() {
    CaffeineLinkCache cache = newCache();
    ShortLink shortLink = newShortLink(Optional.empty());
    cache.put(shortLink.code(), Optional.of(shortLink));

    cache.invalidate(shortLink.code());

    assertThat(cache.get(shortLink.code())).isEmpty();
  }

  @Test
  void invalidatingAnUncachedCodeIsANoOp() {
    CaffeineLinkCache cache = newCache();

    cache.invalidate(ShortCode.reconstitute("nvrissud"));

    assertThat(cache.get(ShortCode.reconstitute("nvrissud"))).isEmpty();
  }

  @Test
  void cacheEvictsOnceBoundedSizeIsExceeded() {
    CaffeineLinkCache cache = newCache(new ManualTime(NOW), 2);

    for (int i = 0; i < 10; i++) {
      ShortLink shortLink = newShortLink(Optional.empty());
      cache.put(shortLink.code(), Optional.of(shortLink));
    }
    cache.cleanUp();

    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2);
  }

  private static CaffeineLinkCache newCache() {
    return newCache(new ManualTime(NOW));
  }

  private static CaffeineLinkCache newCache(ManualTime time) {
    return newCache(time, CaffeineLinkCache.MAXIMUM_SIZE);
  }

  private static CaffeineLinkCache newCache(ManualTime time, long maximumSize) {
    return new CaffeineLinkCache(time.clock(), time.ticker(), maximumSize);
  }

  private static ShortLink newShortLink(Optional<Instant> expiresAt) {
    return new ShortLink(ShortCode.generate(), TARGET_URL, "api-key-1", NOW, expiresAt);
  }

  /** A Clock and a Caffeine Ticker that always agree, so tests can advance both in lockstep. */
  private static final class ManualTime {

    private Instant now;

    ManualTime(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    Clock clock() {
      return new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
          return now;
        }
      };
    }

    Ticker ticker() {
      return () -> Duration.between(Instant.EPOCH, now).toNanos();
    }
  }
}
