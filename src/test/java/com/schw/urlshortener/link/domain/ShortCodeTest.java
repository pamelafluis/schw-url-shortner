package com.schw.urlshortener.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class ShortCodeTest {

  @Test
  void generateProducesSevenBase62Characters() {
    ShortCode code = ShortCode.generate();

    assertThat(code.value()).hasSize(7).matches("[A-Za-z0-9]{7}");
  }

  @Test
  void fromAliasProducesShortCodeWithTheAliasValue() {
    ShortCode code = ShortCode.fromAlias("launch");

    assertThat(code.value()).isEqualTo("launch");
  }

  @Test
  void shortCodesWithTheSameValueAreEqual() {
    ShortCode a = ShortCode.fromAlias("launch");
    ShortCode b = ShortCode.fromAlias("launch");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void aliasComparisonIsCaseSensitive() {
    ShortCode lower = ShortCode.fromAlias("launch");
    ShortCode upper = ShortCode.fromAlias("Launch");

    assertThat(lower).isNotEqualTo(upper);
  }

  @Test
  void fromAliasRejectsAliasShorterThanThreeCharacters() {
    assertThatExceptionOfType(MalformedAliasException.class)
        .isThrownBy(() -> ShortCode.fromAlias("ab"));
  }

  @Test
  void fromAliasAcceptsThreeCharacters() {
    ShortCode code = ShortCode.fromAlias("abc");

    assertThat(code.value()).isEqualTo("abc");
  }

  @Test
  void fromAliasRejectsAliasLongerThanThirtyTwoCharacters() {
    String tooLong = "a".repeat(33);

    assertThatExceptionOfType(MalformedAliasException.class)
        .isThrownBy(() -> ShortCode.fromAlias(tooLong));
  }

  @Test
  void fromAliasAcceptsThirtyTwoCharacters() {
    String maxLength = "a".repeat(32);

    ShortCode code = ShortCode.fromAlias(maxLength);

    assertThat(code.value()).isEqualTo(maxLength);
  }

  @Test
  void fromAliasRejectsCharactersOutsideTheAllowedCharset() {
    assertThatExceptionOfType(MalformedAliasException.class)
        .isThrownBy(() -> ShortCode.fromAlias("bad alias!"));
  }

  @Test
  void fromAliasAcceptsUnderscoreAndHyphen() {
    ShortCode code = ShortCode.fromAlias("launch_day-1");

    assertThat(code.value()).isEqualTo("launch_day-1");
  }

  @Test
  void fromAliasRejectsReservedWords() {
    for (String reserved :
        new String[] {
          "api", "health", "actuator", "metrics", "docs", "robots.txt", "favicon.ico"
        }) {
      assertThatExceptionOfType(ReservedAliasException.class)
          .describedAs("alias '%s' should be reserved", reserved)
          .isThrownBy(() -> ShortCode.fromAlias(reserved));
    }
  }

  @Test
  void reservedWordCheckTakesPrecedenceOverCharsetForRobotsAndFaviconAliases() {
    // robots.txt and favicon.ico contain '.', which also fails the charset rule.
    // They must still be reported as reserved (409), not malformed (400).
    assertThatExceptionOfType(ReservedAliasException.class)
        .isThrownBy(() -> ShortCode.fromAlias("robots.txt"));
    assertThatExceptionOfType(ReservedAliasException.class)
        .isThrownBy(() -> ShortCode.fromAlias("favicon.ico"));
  }

  @Test
  void reservedWordMatchingIsCaseSensitive() {
    ShortCode code = ShortCode.fromAlias("API");

    assertThat(code.value()).isEqualTo("API");
  }

  @Test
  void reconstituteProducesShortCodeWithTheStoredValueWithoutValidation() {
    // A persisted value was already validated once, at creation. Reconstitution must
    // not re-run alias rules — a generated 7-char code could coincidentally collide
    // with a reserved word (e.g. "metrics") and must not be rejected on reload.
    ShortCode code = ShortCode.reconstitute("metrics");

    assertThat(code.value()).isEqualTo("metrics");
  }

  @Test
  void reconstitutedShortCodeIsEqualToAnEquivalentGeneratedOrAliasCode() {
    ShortCode fromAlias = ShortCode.fromAlias("launch");
    ShortCode reconstituted = ShortCode.reconstitute("launch");

    assertThat(reconstituted).isEqualTo(fromAlias).hasSameHashCodeAs(fromAlias);
  }
}
