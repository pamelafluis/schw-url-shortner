package com.schw.urlshortener.link.api;

import java.time.Instant;

record CreateShortLinkRequest(String targetUrl, String alias, Instant expiresAt) {}
