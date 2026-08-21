package com.schw.urlshortener.link.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeTest {

	@Test
	void generateProducesSevenBase62Characters() {
		ShortCode code = ShortCode.generate();

		assertThat(code.value()).hasSize(7).matches("[A-Za-z0-9]{7}");
	}

}
