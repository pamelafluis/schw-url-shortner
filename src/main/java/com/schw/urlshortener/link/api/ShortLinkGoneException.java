package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.ResolutionOutcome;

/**
 * A ShortLink exists but is EXPIRED or DEACTIVATED. Maps to 410, per the README's 404/410 split.
 */
public class ShortLinkGoneException extends RuntimeException {

  public ShortLinkGoneException(String code, ResolutionOutcome outcome) {
    super("ShortCode '%s' is gone (%s)".formatted(code, outcome));
  }
}
