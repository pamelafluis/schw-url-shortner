package com.schw.urlshortener.link.api;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage for the Resolution-outcome counter and redirect timer (issue #13): proves the
 * instrumentation added to {@code ResolveController} is actually wired to the resolve path, not
 * just present in configuration, by exercising a real Resolution and reading the result back
 * through the exposed {@code /actuator/metrics} endpoint.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MetricsIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private ShortLinkRepository repository;
  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int port;

  @Test
  void resolvingAShortLinkIsReflectedInTheResolutionsCounter() {
    ShortLink shortLink = saveShortLink();

    restTemplate.exchange(
        "/" + shortLink.code().value(), HttpMethod.GET, HttpEntity.EMPTY, Void.class);

    ResponseEntity<String> response =
        restTemplate.getForEntity("/actuator/metrics/link.resolutions", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("measurements").get(0).get("value").asDouble()).isGreaterThan(0.0);
  }

  @Test
  void resolvingAShortLinkIsReflectedInTheRedirectTimer() {
    ShortLink shortLink = saveShortLink();

    restTemplate.exchange(
        "/" + shortLink.code().value(), HttpMethod.GET, HttpEntity.EMPTY, Void.class);

    ResponseEntity<String> response =
        restTemplate.getForEntity("/actuator/metrics/link.resolve", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode count = objectMapper.readTree(response.getBody()).get("measurements").get(0);
    assertThat(count.get("statistic").asText()).isEqualTo("COUNT");
    assertThat(count.get("value").asDouble()).isGreaterThan(0.0);
  }

  private ShortLink saveShortLink() {
    TargetUrl targetUrl = TargetUrl.reconstitute("http://localhost:" + port + "/actuator/health");
    return repository.saveGenerated(targetUrl, "dev-key", CREATED_AT, Optional.empty());
  }
}
