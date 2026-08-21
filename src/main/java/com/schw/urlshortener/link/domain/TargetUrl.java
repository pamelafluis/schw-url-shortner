package com.schw.urlshortener.link.domain;

import java.net.URI;
import java.net.URISyntaxException;

public final class TargetUrl {

	private final String value;

	private TargetUrl(String value) {
		this.value = value;
	}

	public static TargetUrl of(String rawUrl, String ownHost, HostnameResolver resolver) {
		URI uri = parse(rawUrl);
		requireHttpOrHttpsScheme(uri, rawUrl);
		requireHost(uri, rawUrl);
		return new TargetUrl(rawUrl);
	}

	private static URI parse(String rawUrl) {
		try {
			return new URI(rawUrl);
		}
		catch (URISyntaxException e) {
			throw new InvalidTargetUrlException("TargetUrl '%s' is not a valid URL".formatted(rawUrl));
		}
	}

	private static void requireHttpOrHttpsScheme(URI uri, String rawUrl) {
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new InvalidTargetUrlException("TargetUrl '%s' must use http or https".formatted(rawUrl));
		}
	}

	private static void requireHost(URI uri, String rawUrl) {
		String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			throw new InvalidTargetUrlException("TargetUrl '%s' has no host".formatted(rawUrl));
		}
	}

	public String value() {
		return value;
	}

}
