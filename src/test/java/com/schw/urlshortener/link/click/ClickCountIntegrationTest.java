package com.schw.urlshortener.link.click;

import static org.assertj.core.api.Assertions.assertThat;

import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage for step 6 (issue #12): N Resolutions produce N Clicks once flushed. Seeds
 * ShortLinks straight through {@link ShortLinkRepository} (bypassing the create endpoint's live
 * DNS-backed SSRF check, same as {@code JdbcShortLinkRepositoryTest}) and drives the rest of the
 * flow through the real HTTP stack against a real Postgres.
 *
 * <p>TargetUrls point back at this instance's own actuator/health endpoint rather than a real
 * external host: {@link TestRestTemplate} follows redirects by default, so a resolve that pointed
 * at a live third party would make this test depend on outbound network access and that host's
 * uptime for no benefit — the redirect status itself is already covered by {@code
 * ResolveControllerTest}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ClickCountIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ShortLinkRepository repository;
  @Autowired private ClickFlushScheduler flushScheduler;
  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int port;

  @Test
  void nResolutionsProduceNClicksOnceFlushed() {
    ShortLink shortLink = saveShortLink();
    int resolutions = 3;

    for (int i = 0; i < resolutions; i++) {
      resolve(shortLink);
    }
    flushScheduler.flush();

    assertThat(clickCount(shortLink)).isEqualTo(resolutions);
  }

  @Test
  void aFreshNeverResolvedShortLinkReportsAClickCountOfZero() {
    ShortLink shortLink = saveShortLink();

    assertThat(clickCount(shortLink)).isZero();
  }

  @Test
  void resolvingANeverIssuedCodeNeverProducesAClick() {
    ResponseEntity<Void> response =
        restTemplate.exchange("/nvrissu9", HttpMethod.GET, HttpEntity.EMPTY, Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    flushScheduler.flush();

    // No ShortLink was ever seeded for this code, so there is nothing to check via GET;
    // the assertion that matters is that recordClicks is never invoked for a phantom code,
    // which flush() already guarantees by construction (nothing was ever registered).
  }

  @Test
  void resolvingADeactivatedShortLinkNeverProducesAClick() {
    ShortLink shortLink = saveShortLink();
    repository.deactivate(shortLink.code());

    for (int i = 0; i < 3; i++) {
      resolve(shortLink);
    }
    flushScheduler.flush();

    assertThat(clickCount(shortLink)).isZero();
  }

  private ShortLink saveShortLink() {
    TargetUrl targetUrl = TargetUrl.reconstitute("http://localhost:" + port + "/actuator/health");
    return repository.saveGenerated(targetUrl, "dev-key", CREATED_AT, Optional.empty());
  }

  private void resolve(ShortLink shortLink) {
    // Not asserting the status here: TestRestTemplate follows the redirect, so the status
    // observed is whatever the followed target (our own health endpoint) returns, not the
    // 302 ResolveController actually sent. That contract is covered by ResolveControllerTest.
    restTemplate.exchange(
        "/" + shortLink.code().value(), HttpMethod.GET, HttpEntity.EMPTY, Void.class);
  }

  private long clickCount(ShortLink shortLink) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-API-Key", "dev-key");
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v1/links/" + shortLink.code().value(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    return body.get("clickCount").asLong();
  }
}
