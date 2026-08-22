package com.schw.urlshortener.link.persistence;

/**
 * The caller's chosen Alias is already taken by another ShortLink. Distinct from a generated-code
 * collision: an Alias is never silently retried with a different value, since that would give the
 * caller a code they didn't ask for.
 */
public class AliasConflictException extends RuntimeException {

  public AliasConflictException(String alias) {
    super("Alias '%s' is already taken".formatted(alias));
  }
}
