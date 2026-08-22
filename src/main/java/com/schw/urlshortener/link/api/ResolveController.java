package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.cache.LinkCache;
import com.schw.urlshortener.link.click.ClickRegistry;
import com.schw.urlshortener.link.domain.ResolutionOutcome;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hot path: resolves a ShortCode to a redirect. Deliberately outside {@code /api/**}, so {@link
 * ApiKeyFilter} never runs here — per the README, resolving requires no auth.
 */
@RestController
class ResolveController {

  private static final String OUTCOME_COUNTER = "link.resolutions";
  private static final String LATENCY_TIMER = "link.resolve";

  private final ShortLinkRepository repository;
  private final LinkCache cache;
  private final Clock clock;
  private final ClickRegistry clickRegistry;
  private final MeterRegistry meterRegistry;

  ResolveController(
      ShortLinkRepository repository,
      LinkCache cache,
      Clock clock,
      ClickRegistry clickRegistry,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.cache = cache;
    this.clock = clock;
    this.clickRegistry = clickRegistry;
    this.meterRegistry = meterRegistry;
  }

  @GetMapping("/{code}")
  ResponseEntity<Void> resolve(@PathVariable String code) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      return doResolve(code);
    } finally {
      sample.stop(meterRegistry.timer(LATENCY_TIMER));
    }
  }

  private ResponseEntity<Void> doResolve(String code) {
    ShortCode shortCode = ShortCode.reconstitute(code);
    Optional<ShortLink> found = load(shortCode);
    if (found.isEmpty()) {
      countOutcome("not-found");
      throw new ShortLinkNotFoundException(code);
    }
    ShortLink shortLink = found.get();

    ResolutionOutcome outcome = shortLink.resolutionOutcome(clock.instant());
    countOutcome(outcomeTag(outcome));
    if (outcome != ResolutionOutcome.RESOLVABLE) {
      throw new ShortLinkGoneException(code, outcome);
    }

    clickRegistry.increment(shortLink.code());

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(shortLink.targetUrl().value()))
        .cacheControl(CacheControl.noStore())
        .build();
  }

  private void countOutcome(String outcome) {
    meterRegistry.counter(OUTCOME_COUNTER, "outcome", outcome).increment();
  }

  private static String outcomeTag(ResolutionOutcome outcome) {
    return switch (outcome) {
      case RESOLVABLE -> "resolvable";
      case EXPIRED -> "expired";
      case DEACTIVATED -> "deactivated";
    };
  }

  // Cache checked before Postgres; a miss is loaded from Postgres and cached either way
  // (negative caching, per ADR-0004, so scans of never-issued codes can't become a DB DoS).
  private Optional<ShortLink> load(ShortCode code) {
    Optional<Optional<ShortLink>> cached = cache.get(code);
    if (cached.isPresent()) {
      return cached.get();
    }
    Optional<ShortLink> loaded = repository.findByCode(code);
    cache.put(code, loaded);
    return loaded;
  }
}
