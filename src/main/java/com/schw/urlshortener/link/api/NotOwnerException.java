package com.schw.urlshortener.link.api;

/** The requesting API key does not match the ShortLink's createdBy. Maps to 403. */
public class NotOwnerException extends RuntimeException {

  public NotOwnerException(String code) {
    super("Caller does not own ShortCode '%s'".formatted(code));
  }
}
