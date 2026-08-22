package com.schw.urlshortener.link.domain;

/**
 * An Alias matched one of the service's own reserved route names. Distinct from {@link
 * MalformedAliasException} so the API layer can map this to 409.
 */
public class ReservedAliasException extends RuntimeException {

  public ReservedAliasException(String rawAlias) {
    super("Alias '%s' is reserved".formatted(rawAlias));
  }
}
