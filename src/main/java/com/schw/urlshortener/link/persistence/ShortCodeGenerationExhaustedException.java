package com.schw.urlshortener.link.persistence;

/**
 * A generated ShortCode collided on every retry attempt. Per ADR-0001 the
 * seven-character base62 space (~3.5x10^12) makes this vanishingly unlikely
 * at the stated 100M-link corpus size — this is a pathological-case guard,
 * not an expected outcome.
 */
public class ShortCodeGenerationExhaustedException extends RuntimeException {

	public ShortCodeGenerationExhaustedException(int maxRetries, Throwable lastConflict) {
		super("Failed to generate a unique ShortCode after the initial attempt and %d retries".formatted(maxRetries),
				lastConflict);
	}

}
