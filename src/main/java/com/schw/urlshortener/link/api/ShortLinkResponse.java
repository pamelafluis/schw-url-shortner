package com.schw.urlshortener.link.api;

import com.schw.urlshortener.link.domain.ShortLink;
import java.time.Instant;

record ShortLinkResponse(
    String code,
    String shortUrl,
    String targetUrl,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    boolean active,
    long clickCount) {

  static ShortLinkResponse from(ShortLink shortLink, String baseUrl) {
    return new ShortLinkResponse(
        shortLink.code().value(),
        baseUrl + "/" + shortLink.code().value(),
        shortLink.targetUrl().value(),
        shortLink.createdBy(),
        shortLink.createdAt(),
        shortLink.expiresAt().orElse(null),
        shortLink.active(),
        shortLink.clickCount());
  }
}
