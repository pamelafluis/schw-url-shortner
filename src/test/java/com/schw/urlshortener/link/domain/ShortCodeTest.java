package com.schw.urlshortener.link.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

}
