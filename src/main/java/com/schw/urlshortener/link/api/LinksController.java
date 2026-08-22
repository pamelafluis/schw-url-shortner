package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.cache.LinkCache;
import com.schw.urlshortener.link.domain.HostnameResolver;
import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

  private static final String PROBLEM_JSON = "application/problem+json";

  private final ShortLinkRepository repository;
  private final LinkCache cache;
  private final HostnameResolver hostnameResolver;
  private final Clock clock;
  private final String ownHost;
  private final String baseUrl;

  LinksController(
      ShortLinkRepository repository,
      LinkCache cache,
      HostnameResolver hostnameResolver,
      Clock clock,
      @Value("${app.own-host}") String ownHost,
      @Value("${app.base-url}") String baseUrl) {
    this.repository = repository;
    this.cache = cache;
    this.hostnameResolver = hostnameResolver;
    this.clock = clock;
    this.ownHost = ownHost;
    this.baseUrl = baseUrl;
  }

  @Operation(summary = "Create a ShortLink")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Created",
        content = @Content(schema = @Schema(implementation = ShortLinkResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid TargetUrl, malformed alias, or malformed request body",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid API key",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Alias is reserved or already in use",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
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

  @Operation(summary = "Get ShortLink metadata and Click count")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = ShortLinkResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid API key",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ShortCode was never issued",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @GetMapping("/{code}")
  ShortLinkResponse get(@PathVariable String code) {
    ShortLink shortLink = findOrThrow(code);
    return ShortLinkResponse.from(shortLink, baseUrl);
  }

  @Operation(summary = "Deactivate (soft-delete) a ShortLink")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Deactivated"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid API key",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Caller is not the owner",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "ShortCode was never issued",
        content =
            @Content(
                mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  @DeleteMapping("/{code}")
  ResponseEntity<Void> deactivate(
      @PathVariable String code, @RequestAttribute("apiKey") String apiKey) {
    ShortLink shortLink = findOrThrow(code);
    if (!shortLink.createdBy().equals(apiKey)) {
      throw new NotOwnerException(code);
    }
    repository.deactivate(shortLink.code());
    cache.invalidate(shortLink.code());
    return ResponseEntity.noContent().build();
  }

  private ShortLink findOrThrow(String code) {
    return repository
        .findByCode(ShortCode.reconstitute(code))
        .orElseThrow(() -> new ShortLinkNotFoundException(code));
  }
}
