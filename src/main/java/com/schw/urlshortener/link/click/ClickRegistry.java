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

  public void increment(ShortCode code) {
    counters.computeIfAbsent(code, c -> new LongAdder()).increment();
  }

  /**
   * Atomically reads and resets every pending delta. A concurrent {@link #increment} for a
   * ShortCode already present in the registry lands either in this drain or the next one, never
   * dropped — see {@link LongAdder#sumThenReset()}.
   */
  public Map<ShortCode, Long> drain() {
    Map<ShortCode, Long> deltas = new HashMap<>();
    counters.forEach(
        (code, adder) -> {
          long delta = adder.sumThenReset();
          if (delta != 0) {
            deltas.put(code, delta);
          }
        });
    return deltas;
  }
}
