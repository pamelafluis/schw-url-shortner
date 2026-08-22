package com.schw.urlshortener.link.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("short_link")
record ShortLinkEntity(@Id String shortCode, String targetUrl, String createdBy, Instant createdAt,
		Instant expiresAt, boolean active) {

}
