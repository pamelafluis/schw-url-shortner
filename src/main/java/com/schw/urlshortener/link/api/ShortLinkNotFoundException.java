package com.schw.urlshortener.link.api;

/** No ShortLink has ever been issued for the given ShortCode. Maps to 404. */
public class ShortLinkNotFoundException extends RuntimeException {

  public ShortLinkNotFoundException(String code) {
    super("ShortCode '%s' was never issued".formatted(code));
  }
}
