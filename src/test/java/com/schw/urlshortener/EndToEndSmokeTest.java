package com.schw.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The first point the API, persistence, and cache layers all exist together — one smoke test
 * proving the assembled system is wired correctly end to end (create → resolve → deactivate →
 * resolve again), not a place to re-test business rules already covered by the domain, contract,
 * and persistence tests.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class EndToEndSmokeTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void createResolveDeactivateThenResolveAgain() {
    TestRestTemplate noRedirects = restTemplate.withRedirects(HttpRedirects.DONT_FOLLOW);
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-API-Key", "dev-key");
    headers.setContentType(MediaType.APPLICATION_JSON);

    ResponseEntity<Map> created =
        restTemplate.postForEntity(
            "/api/v1/links",
            new HttpEntity<>("{\"targetUrl\":\"https://example.com/x\"}", headers),
            Map.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String code = (String) created.getBody().get("code");

    ResponseEntity<Void> firstResolve = noRedirects.getForEntity("/" + code, Void.class);
    assertThat(firstResolve.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(firstResolve.getHeaders().getLocation()).hasToString("https://example.com/x");

    ResponseEntity<Void> deactivated =
        restTemplate.exchange(
            "/api/v1/links/" + code, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<String> secondResolve = noRedirects.getForEntity("/" + code, String.class);
    assertThat(secondResolve.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }
}
