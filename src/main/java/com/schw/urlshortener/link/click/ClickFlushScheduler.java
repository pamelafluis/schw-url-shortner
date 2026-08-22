package com.schw.urlshortener.link.click;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains {@link ClickRegistry} to Postgres every 5 seconds (ADR-0003's stated lag budget), plus
 * once more on graceful shutdown so a normal restart or redeploy loses nothing.
 */
@Component
class ClickFlushScheduler {

  private final ClickRegistry registry;
  private final ShortLinkRepository repository;

  ClickFlushScheduler(ClickRegistry registry, ShortLinkRepository repository) {
    this.registry = registry;
    this.repository = repository;
  }

  // Package-visible test seam: lets a test trigger a deterministic flush instead of
  // waiting on the real 5-second timer.
  @Scheduled(fixedRate = 5000)
  void flush() {
    Map<ShortCode, Long> deltas = registry.drain();
    if (!deltas.isEmpty()) {
      repository.recordClicks(deltas);
    }
  }

  @PreDestroy
  void flushOnShutdown() {
    flush();
  }
}
