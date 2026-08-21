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

	@Test
	void rejectsLocalhostRegardlessOfCase() {
		HostnameResolver resolver = refusingResolver();

		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("http://localhost/x", OWN_HOST, resolver));
		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("http://LocalHost/x", OWN_HOST, resolver));
	}

	@Test
	void rejectsLoopbackIpLiteralWithoutConsultingTheResolver() {
		HostnameResolver resolver = refusingResolver();

		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("http://127.0.0.1/x", OWN_HOST, resolver));
	}

	@Test
	void rejectsPrivateRangeIpLiterals() {
		HostnameResolver resolver = refusingResolver();

		for (String ip : new String[] {"10.0.0.5", "172.16.0.5", "192.168.1.5"}) {
			assertThatExceptionOfType(InvalidTargetUrlException.class)
					.describedAs("private IP literal '%s' should be rejected", ip)
					.isThrownBy(() -> TargetUrl.of("http://" + ip + "/x", OWN_HOST, resolver));
		}
	}

	@Test
	void rejectsLinkLocalIpLiteralIncludingCloudMetadataAddress() {
		HostnameResolver resolver = refusingResolver();

		assertThatExceptionOfType(InvalidTargetUrlException.class)
				.isThrownBy(() -> TargetUrl.of("http://169.254.169.254/x", OWN_HOST, resolver));
	}

	@Test
	void acceptsAPublicIpLiteralWithoutConsultingTheResolver() {
		HostnameResolver resolver = refusingResolver();

		TargetUrl targetUrl = TargetUrl.of("http://93.184.216.34/x", OWN_HOST, resolver);

		assertThat(targetUrl.value()).isEqualTo("http://93.184.216.34/x");
	}

	private static HostnameResolver refusingResolver() {
		return host -> {
			throw new AssertionError("resolver should not be consulted for an IP literal host: " + host);
		};
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
