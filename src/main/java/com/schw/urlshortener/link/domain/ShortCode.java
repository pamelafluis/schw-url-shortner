package com.schw.urlshortener.link.domain;

import java.security.SecureRandom;
import java.util.Objects;

public final class ShortCode {

	private static final String BASE62_ALPHABET =
			"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final int LENGTH = 7;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String value;

	private ShortCode(String value) {
		this.value = value;
	}

	public static ShortCode generate() {
		StringBuilder builder = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			builder.append(BASE62_ALPHABET.charAt(RANDOM.nextInt(BASE62_ALPHABET.length())));
		}
		return new ShortCode(builder.toString());
	}

	public static ShortCode fromAlias(String rawAlias) {
		return new ShortCode(rawAlias);
	}

	public String value() {
		return value;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ShortCode otherCode)) {
			return false;
		}
		return value.equals(otherCode.value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

	@Override
	public String toString() {
		return value;
	}

}
