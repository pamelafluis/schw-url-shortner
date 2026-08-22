package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.HostnameResolver;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create, inspect, and deactivate ShortLinks. Sits behind {@link ApiKeyFilter}, so every method
 * here runs with a valid {@code X-API-Key} already confirmed.
 */
@RestController
@RequestMapping("/api/v1/links")
class LinksController {

  private final ShortLinkRepository repository;
  private final HostnameResolver hostnameResolver;
  private final Clock clock;
  private final String ownHost;
  private final String baseUrl;

  LinksController(
      ShortLinkRepository repository,
      HostnameResolver hostnameResolver,
      Clock clock,
      @Value("${app.own-host}") String ownHost,
      @Value("${app.base-url}") String baseUrl) {
    this.repository = repository;
    this.hostnameResolver = hostnameResolver;
    this.clock = clock;
    this.ownHost = ownHost;
    this.baseUrl = baseUrl;
  }

  @PostMapping
  ResponseEntity<ShortLinkResponse> create(
      @RequestBody CreateShortLinkRequest request, @RequestAttribute("apiKey") String apiKey) {
    TargetUrl targetUrl = TargetUrl.of(request.targetUrl(), ownHost, hostnameResolver);
    Instant now = clock.instant();
    Optional<Instant> expiresAt = Optional.ofNullable(request.expiresAt());

    ShortLink shortLink =
        request.alias() == null
            ? repository.saveGenerated(targetUrl, apiKey, now, expiresAt)
            : repository.saveWithAlias(
                ShortCode.fromAlias(request.alias()), targetUrl, apiKey, now, expiresAt);

    return ResponseEntity.status(HttpStatus.CREATED)
        .location(URI.create("/api/v1/links/" + shortLink.code().value()))
        .body(ShortLinkResponse.from(shortLink, baseUrl));
  }

  @GetMapping("/{code}")
  ShortLinkResponse get(@PathVariable String code) {
    ShortLink shortLink = findOrThrow(code);
    return ShortLinkResponse.from(shortLink, baseUrl);
  }

  @DeleteMapping("/{code}")
  ResponseEntity<Void> deactivate(
      @PathVariable String code, @RequestAttribute("apiKey") String apiKey) {
    ShortLink shortLink = findOrThrow(code);
    if (!shortLink.createdBy().equals(apiKey)) {
      throw new NotOwnerException(code);
    }
    repository.deactivate(shortLink.code());
    return ResponseEntity.noContent().build();
  }

  private ShortLink findOrThrow(String code) {
    return repository
        .findByCode(ShortCode.reconstitute(code))
        .orElseThrow(() -> new ShortLinkNotFoundException(code));
  }
}
