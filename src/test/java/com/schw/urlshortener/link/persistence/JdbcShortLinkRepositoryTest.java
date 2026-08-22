package com.schw.urlshortener.link.persistence;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.domain.ShortLink;
import com.schw.urlshortener.link.domain.TargetUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class JdbcShortLinkRepositoryTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
	private static final TargetUrl TARGET_URL = TargetUrl.reconstitute("https://example.com/x");

	@Autowired
	private ShortLinkRepository repository;

	@Test
	void savedGeneratedShortLinkIsFoundByItsCode() {
		ShortLink saved = repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

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

		ShortLink saved = repository.saveWithAlias(alias, TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

		assertThat(saved.code()).isEqualTo(alias);
		assertThat(repository.findByCode(alias)).isPresent();
	}

	@Test
	void expiresAtRoundTripsThroughPersistence() {
		Instant expiresAt = CREATED_AT.plusSeconds(3600);

		ShortLink saved = repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.of(expiresAt));

		assertThat(repository.findByCode(saved.code()).get().expiresAt()).contains(expiresAt);
	}

	@Test
	void deactivatePersistsAndIsVisibleOnReload() {
		ShortLink saved = repository.saveGenerated(TARGET_URL, "api-key-1", CREATED_AT, Optional.empty());

		repository.deactivate(saved.code());

		Optional<ShortLink> reloaded = repository.findByCode(saved.code());
		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().active()).isFalse();
	}

}
