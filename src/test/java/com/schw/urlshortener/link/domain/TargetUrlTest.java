package com.schw.urlshortener.link.domain;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TargetUrlTest {

	private static final String OWN_HOST = "sho.rt";

	@Test
	void acceptsAValidHttpsUrlWithAPubliclyResolvableHost() {
		HostnameResolver resolver = resolverReturning("93.184.216.34");

		TargetUrl targetUrl = TargetUrl.of("https://example.com/path", OWN_HOST, resolver);

		assertThat(targetUrl.value()).isEqualTo("https://example.com/path");
	}

	@Test
	void rejectsSchemesOtherThanHttpAndHttps() {
		HostnameResolver resolver = resolverReturning("93.184.216.34");

		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("javascript:alert(1)", OWN_HOST, resolver));
	}

	@Test
	void rejectsAUrlWhoseHostDoesNotParse() {
		HostnameResolver resolver = resolverReturning("93.184.216.34");

		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("https:///no-host", OWN_HOST, resolver));
	}

	private static HostnameResolver resolverReturning(String... literalIps) {
		List<InetAddress> addresses = Arrays.stream(literalIps)
				.map(TargetUrlTest::literalAddress)
				.toList();
		return host -> addresses;
	}

	private static InetAddress literalAddress(String ip) {
		try {
			return InetAddress.getByName(ip);
		}
		catch (UnknownHostException e) {
			throw new AssertionError(e);
		}
	}

}
