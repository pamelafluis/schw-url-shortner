package com.schw.urlshortener.link.domain;

import static com.schw.urlshortener.link.domain.HostnameResolverFixtures.refusingResolver;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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

    assertThat(link.resolutionOutcome(expiresAt.minusSeconds(1)))
        .isEqualTo(ResolutionOutcome.RESOLVABLE);
  }

  @Test
  void isResolvableAtTheExactExpiryInstant() {
    // expiresAt is the last resolvable instant (isAfter is exclusive) — locking in
    // this boundary choice since the spec doesn't state inclusive/exclusive.
    Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
    ShortLink link = newShortLink(Optional.of(expiresAt));

    assertThat(link.resolutionOutcome(expiresAt)).isEqualTo(ResolutionOutcome.RESOLVABLE);
  }

  @Test
  void isExpiredWhenNowIsAfterExpiry() {
    Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
    ShortLink link = newShortLink(Optional.of(expiresAt));

    assertThat(link.resolutionOutcome(expiresAt.plusSeconds(1)))
        .isEqualTo(ResolutionOutcome.EXPIRED);
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

    assertThat(link.resolutionOutcome(expiresAt.plusSeconds(1)))
        .isEqualTo(ResolutionOutcome.DEACTIVATED);
  }

  @Test
  void exposesTheConstructorArgumentsAsData() {
    Instant expiresAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
    ShortCode code = ShortCode.generate();
    TargetUrl targetUrl = TargetUrl.of("https://93.184.216.34/x", "sho.rt", refusingResolver());

    ShortLink link =
        new ShortLink(code, targetUrl, "api-key-1", CREATED_AT, Optional.of(expiresAt));

    assertThat(link.code()).isEqualTo(code);
    assertThat(link.targetUrl()).isEqualTo(targetUrl);
    assertThat(link.createdBy()).isEqualTo("api-key-1");
    assertThat(link.createdAt()).isEqualTo(CREATED_AT);
    assertThat(link.expiresAt()).contains(expiresAt);
    assertThat(link.active()).isTrue();
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
}
