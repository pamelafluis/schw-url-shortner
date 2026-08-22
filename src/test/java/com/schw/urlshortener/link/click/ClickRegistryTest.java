package com.schw.urlshortener.link.click;

import static org.assertj.core.api.Assertions.assertThat;

import com.schw.urlshortener.link.domain.ShortCode;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ClickRegistryTest {

  private static final ShortCode CODE_A = ShortCode.reconstitute("aB3xK9z");
  private static final ShortCode CODE_B = ShortCode.reconstitute("zZ9yX1a");

  @Test
  void drainWithNothingPendingReturnsAnEmptyResult() {
    ClickRegistry registry = new ClickRegistry();

    assertThat(registry.drain()).isEmpty();
  }

  @Test
  void drainReturnsExactlyWhatAccumulatedAndResetsToZero() {
    ClickRegistry registry = new ClickRegistry();

    registry.increment(CODE_A);
    registry.increment(CODE_A);
    registry.increment(CODE_B);

    Map<ShortCode, Long> firstDrain = registry.drain();

    assertThat(firstDrain).containsExactlyInAnyOrderEntriesOf(Map.of(CODE_A, 2L, CODE_B, 1L));
    assertThat(registry.drain()).isEmpty();
  }

  @Test
  void concurrentIncrementsOnTheSameShortCodeAllAccumulate() throws InterruptedException {
    ClickRegistry registry = new ClickRegistry();
    int threadCount = 16;
    int incrementsPerThread = 500;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);

    for (int i = 0; i < threadCount; i++) {
      pool.submit(
          () -> {
            ready.countDown();
            await(start);
            for (int j = 0; j < incrementsPerThread; j++) {
              registry.increment(CODE_A);
            }
          });
    }
    ready.await();
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    assertThat(registry.drain()).containsEntry(CODE_A, (long) threadCount * incrementsPerThread);
  }

  @Test
  void incrementsRacingWithADrainAreNeverLost() throws InterruptedException {
    ClickRegistry registry = new ClickRegistry();
    int incrementsPerThread = 2000;
    ExecutorService pool = Executors.newFixedThreadPool(2);

    pool.submit(
        () -> {
          for (int j = 0; j < incrementsPerThread; j++) {
            registry.increment(CODE_A);
          }
        });
    long observed = 0;
    for (int i = 0; i < 50; i++) {
      observed += registry.drain().getOrDefault(CODE_A, 0L);
    }
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    // Catch whatever landed after the last drain in the loop above.
    observed += registry.drain().getOrDefault(CODE_A, 0L);

    assertThat(observed).isEqualTo(incrementsPerThread);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
