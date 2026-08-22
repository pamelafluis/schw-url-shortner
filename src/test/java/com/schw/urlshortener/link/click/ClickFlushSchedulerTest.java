package com.schw.urlshortener.link.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.schw.urlshortener.link.domain.ShortCode;
import com.schw.urlshortener.link.persistence.ShortLinkRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickFlushSchedulerTest {

  private static final ShortCode CODE = ShortCode.reconstitute("aB3xK9z");

  @Mock private ShortLinkRepository repository;

  @Test
  void flushPersistsWhateverTheRegistryHadAccumulated() {
    ClickRegistry registry = new ClickRegistry();
    registry.increment(CODE);
    registry.increment(CODE);
    ClickFlushScheduler scheduler = new ClickFlushScheduler(registry, repository);

    scheduler.flush();

    verify(repository).recordClicks(Map.of(CODE, 2L));
  }

  @Test
  void flushWithNothingPendingNeverCallsTheRepository() {
    ClickRegistry registry = new ClickRegistry();
    ClickFlushScheduler scheduler = new ClickFlushScheduler(registry, repository);

    scheduler.flush();

    verify(repository, never()).recordClicks(anyMap());
  }

  @Test
  void flushDrainsTheRegistrySoASecondFlushHasNothingLeftToPersist() {
    ClickRegistry registry = new ClickRegistry();
    registry.increment(CODE);
    ClickFlushScheduler scheduler = new ClickFlushScheduler(registry, repository);

    scheduler.flush();
    scheduler.flush();

    verify(repository).recordClicks(Map.of(CODE, 1L));
    verify(repository, never()).recordClicks(Map.of());
  }

  @Test
  void shutdownPerformsOneFinalFlush() {
    ClickRegistry registry = new ClickRegistry();
    registry.increment(CODE);
    ClickFlushScheduler scheduler = new ClickFlushScheduler(registry, repository);

    scheduler.flushOnShutdown();

    verify(repository).recordClicks(Map.of(CODE, 1L));
    assertThat(registry.drain()).isEmpty();
  }
}
