package com.schw.urlshortener.link.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("short_link")
record ShortLinkEntity(
    @Id String shortCode,
    String targetUrl,
    String createdBy,
    Instant createdAt,
    Instant expiresAt,
    boolean active) {

  ShortLinkEntity deactivated() {
    return new ShortLinkEntity(shortCode, targetUrl, createdBy, createdAt, expiresAt, false);
  }
}
