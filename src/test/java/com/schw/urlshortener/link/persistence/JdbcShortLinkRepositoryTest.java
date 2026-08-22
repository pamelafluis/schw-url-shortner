package com.schw.urlshortener.link.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class JdbcShortLinkRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final TargetUrl TARGET_URL = TargetUrl.reconstitute("https://example.com/x");

  @Autowired private ShortLinkRepository repository;

  @Autowired private JdbcAggregateTemplate template;

  @Test
  void savedGeneratedShortLinkIsFoundByItsCode() {
    ShortLink saved =
        repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

    Optional<ShortLink> found = repository.findByCode(saved.code());

    assertThat(found).isPresent();
    assertThat(found.get().code()).isEqualTo(saved.code());
    assertThat(found.get().targetUrl().value()).isEqualTo(TARGET_URL.value());
    assertThat(found.get().createdBy()).isEqualTo("api-key-1");
    assertThat(found.get().createdAt()).isEqualTo(CREATED_AT);
    assertThat(found.get().expiresAt()).isEmpty();
    assertThat(found.get().active()).isTrue();
  }

  @Test
  void findByCodeReturnsEmptyForACodeThatWasNeverIssued() {
    Optional<ShortLink> found = repository.findByCode(ShortCode.reconstitute("nvrissu1"));

    assertThat(found).isEmpty();
  }

  @Test
  void savedAliasShortLinkIsFoundByItsAlias() {
    ShortCode alias = ShortCode.fromAlias("core-launch");

    ShortLink saved =
        repository.saveWithAlias(alias, TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

    assertThat(saved.code()).isEqualTo(alias);
    assertThat(repository.findByCode(alias)).isPresent();
  }

  @Test
  void expiresAtRoundTripsThroughPersistence() {
    Instant expiresAt = CREATED_AT.plusSeconds(3600);

    ShortLink saved =
        repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.of(expiresAt));

    assertThat(repository.findByCode(saved.code()).get().expiresAt()).contains(expiresAt);
  }

  @Test
  void deactivatePersistsAndIsVisibleOnReload() {
    ShortLink saved =
        repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

    repository.deactivate(saved.code());

    Optional<ShortLink> reloaded = repository.findByCode(saved.code());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().active()).isFalse();
  }

  @Test
  void savingTheSameAliasTwiceThrowsAliasConflictWithoutSubstitutingAnotherCode() {
    ShortCode alias = ShortCode.fromAlias("conflict-launch");
    repository.saveWithAlias(alias, TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

    assertThatExceptionOfType(AliasConflictException.class)
        .isThrownBy(
            () ->
                repository.saveWithAlias(
                    alias, TARGET_URL, "api-key-2", CREATED_AT, Optional.empty()));
  }

  @Test
  void generatedSaveRetriesOnCollisionAndSucceedsWithADifferentCode() {
    ShortCode taken = ShortCode.reconstitute("retry-taken");
    ShortCode fresh = ShortCode.reconstitute("retry-fresh");
    repository.saveWithAlias(taken, TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());
    Iterator<ShortCode> attempts = List.of(taken, fresh).iterator();
    JdbcShortLinkRepository retrying = new JdbcShortLinkRepository(template, attempts::next);

    ShortLink saved = retrying.saveGenerated(TARGET_URL, "api-key-2", CREATED_AT, Optional.empty());

    assertThat(saved.code()).isEqualTo(fresh);
  }

  @Test
  void generatedSaveThrowsAfterExhaustingRetries() {
    ShortCode taken = ShortCode.reconstitute("retry-exhausted");
    repository.saveWithAlias(taken, TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());
    JdbcShortLinkRepository alwaysColliding = new JdbcShortLinkRepository(template, () -> taken);

    assertThatExceptionOfType(ShortCodeGenerationExhaustedException.class)
        .isThrownBy(
            () ->
                alwaysColliding.saveGenerated(
                    TARGET_URL, "api-key-2", CREATED_AT, Optional.empty()));
  }

  @Test
  void deactivatingACodeThatWasNeverIssuedThrows() {
    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> repository.deactivate(ShortCode.reconstitute("nvrissu2")));
  }
}
