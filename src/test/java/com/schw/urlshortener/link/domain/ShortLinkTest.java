package com.schw.urlshortener.link.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkTest {

	private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	void isResolvableWhenActiveAndNeverExpires() {
		ShortLink link = newShortLink(Optional.empty());

		assertThat(link.resolutionOutcome(Instant.now())).isEqualTo(ResolutionOutcome.RESOLVABLE);
	}

	@Test
	void isResolvableWhenActiveAndExpiryIsInTheFuture() {
		Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
		ShortLink link = newShortLink(Optional.of(expiresAt));

		assertThat(link.resolutionOutcome(expiresAt.minusSeconds(1))).isEqualTo(ResolutionOutcome.RESOLVABLE);
	}

	@Test
	void isExpiredWhenNowIsAfterExpiry() {
		Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
		ShortLink link = newShortLink(Optional.of(expiresAt));

		assertThat(link.resolutionOutcome(expiresAt.plusSeconds(1))).isEqualTo(ResolutionOutcome.EXPIRED);
	}

	@Test
	void isDeactivatedAfterDeactivateEvenWithoutExpiry() {
		ShortLink link = newShortLink(Optional.empty());

		link.deactivate();

		assertThat(link.resolutionOutcome(Instant.now())).isEqualTo(ResolutionOutcome.DEACTIVATED);
	}

	@Test
	void reportsDeactivatedWhenBothExpiredAndDeactivated() {
		Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
		ShortLink link = newShortLink(Optional.of(expiresAt));

		link.deactivate();

		assertThat(link.resolutionOutcome(expiresAt.plusSeconds(1))).isEqualTo(ResolutionOutcome.DEACTIVATED);
	}

	@Test
	void deactivationNeverChangesTheShortCode() {
		ShortLink link = newShortLink(Optional.empty());
		ShortCode codeBeforeDeactivation = link.code();

		link.deactivate();

		assertThat(link.code()).isEqualTo(codeBeforeDeactivation);
	}

	private static ShortLink newShortLink(Optional<Instant> expiresAt) {
		ShortCode code = ShortCode.generate();
		TargetUrl targetUrl = TargetUrl.of("https://93.184.216.34/x", "sho.rt", refusingResolver());
		return new ShortLink(code, targetUrl, "api-key-1", CREATED_AT, expiresAt);
	}

	private static HostnameResolver refusingResolver() {
		return host -> {
			throw new AssertionError("resolver should not be consulted for an IP literal host: " + host);
		};
	}

}
