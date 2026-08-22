package com.schw.urlshortener.link.domain;

/**
 * An Alias failed the charset/length rule: {@code [A-Za-z0-9_-]}, 3-32 characters.
 * Distinct from {@link ReservedAliasException} so the API layer can map this to 400.
 */
public class MalformedAliasException extends RuntimeException {

	public MalformedAliasException(String rawAlias) {
		super("Alias '%s' must be 3-32 characters of [A-Za-z0-9_-]".formatted(rawAlias));
	}

}
