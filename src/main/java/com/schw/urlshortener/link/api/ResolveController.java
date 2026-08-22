package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.cache.LinkCache;
import com.schw.urlshortener.link.domain.ResolutionOutcome;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
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

  private final ShortLinkRepository repository;
  private final LinkCache cache;
  private final Clock clock;

  ResolveController(ShortLinkRepository repository, LinkCache cache, Clock clock) {
    this.repository = repository;
    this.cache = cache;
    this.clock = clock;
  }

  @GetMapping("/{code}")
  ResponseEntity<Void> resolve(@PathVariable String code) {
    ShortCode shortCode = ShortCode.reconstitute(code);
    ShortLink shortLink = load(shortCode).orElseThrow(() -> new ShortLinkNotFoundException(code));

    ResolutionOutcome outcome = shortLink.resolutionOutcome(clock.instant());
    if (outcome != ResolutionOutcome.RESOLVABLE) {
      throw new ShortLinkGoneException(code, outcome);
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(shortLink.targetUrl().value()))
        .cacheControl(CacheControl.noStore())
        .build();
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
