package com.schw.urlshortener.link.domain;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

public final class TargetUrl {

	private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

	private final String value;

	private TargetUrl(String value) {
		this.value = value;
	}

	public static TargetUrl of(String rawUrl, String ownHost, HostnameResolver resolver) {
		URI uri = parse(rawUrl);
		requireHttpOrHttpsScheme(uri, rawUrl);
		requireAllowedHost(uri, rawUrl, resolver);
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

	private static void requireAllowedHost(URI uri, String rawUrl, HostnameResolver resolver) {
		String host = uri.getHost();
		if (host == null || host.isEmpty()) {
			throw new InvalidTargetUrlException("TargetUrl '%s' has no host".formatted(rawUrl));
		}
		if (host.equalsIgnoreCase("localhost")) {
			throw new InvalidTargetUrlException("TargetUrl '%s' targets localhost".formatted(rawUrl));
		}
		if (isIpLiteral(host)) {
			requireNotBlocked(parseLiteral(host, rawUrl), rawUrl);
		}
		else {
			for (InetAddress address : resolve(host, rawUrl, resolver)) {
				requireNotBlocked(address, rawUrl);
			}
		}
	}

	private static List<InetAddress> resolve(String host, String rawUrl, HostnameResolver resolver) {
		try {
			return resolver.resolve(host);
		}
		catch (UnknownHostException e) {
			throw new InvalidTargetUrlException("TargetUrl '%s' host does not resolve".formatted(rawUrl));
		}
	}

	private static boolean isIpLiteral(String host) {
		return IPV4_LITERAL.matcher(host).matches() || host.contains(":");
	}

	private static InetAddress parseLiteral(String literal, String rawUrl) {
		try {
			return InetAddress.getByName(literal);
		}
		catch (UnknownHostException e) {
			throw new InvalidTargetUrlException("TargetUrl '%s' has an unparseable host".formatted(rawUrl));
		}
	}

	private static void requireNotBlocked(InetAddress address, String rawUrl) {
		if (address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
			throw new InvalidTargetUrlException(
					"TargetUrl '%s' resolves to a blocked (loopback/private/link-local) address".formatted(rawUrl));
		}
	}

	public String value() {
		return value;
	}

}
