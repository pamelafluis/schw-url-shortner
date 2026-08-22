package com.schw.urlshortener.link.click;

import com.schw.urlshortener.link.domain.ShortCode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-process, per-ShortCode Click counter (ADR-0003). Deliberately Spring-free: it is the seam a
 * scheduled flush, a shutdown hook, and a test can all call directly, with no database or Spring
 * context required to exercise its counting logic.
 */
public final class ClickRegistry {

  private final ConcurrentHashMap<ShortCode, LongAdder> counters = new ConcurrentHashMap<>();
  private final Map<ShortCode, Long> lastDrainedTotals = new HashMap<>();

  public void increment(ShortCode code) {
    counters.computeIfAbsent(code, c -> new LongAdder()).increment();
  }

  /**
   * Reads every pending delta since the last drain. Uses {@link LongAdder#sum()} — a non-resetting
   * read — and diffs against the cumulative total seen last time, rather than {@link
   * LongAdder#sumThenReset()}, whose plain (non-atomic) per-cell reset can silently wipe out an
   * {@link #increment} that lands between that cell's read and its reset. Diffing against a running
   * total instead means a concurrent increment is always captured, in this drain if it lands before
   * the {@code sum()} read, otherwise in the next one — never dropped.
   */
  public synchronized Map<ShortCode, Long> drain() {
    Map<ShortCode, Long> deltas = new HashMap<>();
    counters.forEach(
        (code, adder) -> {
          long total = adder.sum();
          long delta = total - lastDrainedTotals.getOrDefault(code, 0L);
          if (delta != 0) {
            deltas.put(code, delta);
            lastDrainedTotals.put(code, total);
          }
        });
    return deltas;
  }
}
