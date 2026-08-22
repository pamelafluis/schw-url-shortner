package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.ResolutionOutcome;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.net.URI;
import java.time.Clock;
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
  private final Clock clock;

  ResolveController(ShortLinkRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @GetMapping("/{code}")
  ResponseEntity<Void> resolve(@PathVariable String code) {
    ShortLink shortLink =
        repository
            .findByCode(ShortCode.reconstitute(code))
            .orElseThrow(() -> new ShortLinkNotFoundException(code));

    ResolutionOutcome outcome = shortLink.resolutionOutcome(clock.instant());
    if (outcome != ResolutionOutcome.RESOLVABLE) {
      throw new ShortLinkGoneException(code, outcome);
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(shortLink.targetUrl().value()))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
