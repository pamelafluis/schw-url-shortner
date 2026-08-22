package com.schw.urlshortener.link.domain;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class ShortCode {

  private static final String BASE62_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final int LENGTH = 7;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Pattern ALIAS_CHARSET = Pattern.compile("[A-Za-z0-9_-]{3,32}");

  // Checked verbatim against the raw alias, before charset validation: robots.txt
  // and favicon.ico contain '.', which would otherwise fail as malformed (400)
  // instead of reserved (409).
  private static final Set<String> RESERVED_ALIASES =
      Set.of("api", "health", "actuator", "metrics", "docs", "robots.txt", "favicon.ico");

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
    if (RESERVED_ALIASES.contains(rawAlias)) {
      throw new ReservedAliasException(rawAlias);
    }
    if (!ALIAS_CHARSET.matcher(rawAlias).matches()) {
      throw new MalformedAliasException(rawAlias);
    }
    return new ShortCode(rawAlias);
  }

  /**
   * Rehydrates a ShortCode from a value already validated once, at creation (a persisted row).
   * Skips charset/reserved-word checks, which are creation-time rules, not invariants of the value
   * itself.
   */
  public static ShortCode reconstitute(String persistedValue) {
    return new ShortCode(persistedValue);
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
