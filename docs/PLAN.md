# Build Order

**Current status:** step 7 complete — structured JSON logging with request-ID correlation, Micrometer Resolution-outcome counter and redirect timer, OpenAPI description of `/api/v1/links`, and a GitHub Actions CI workflow running `./mvnw verify` on push/PR. Next: step 8, docs close-out.

Requirements, architecture, and the four load-bearing decisions were settled before any code was written; they live in [`../README.md`](../README.md), [`../CONTEXT.md`](../CONTEXT.md), and [`adr/`](./adr/).

**This file is kept current as a matter of discipline:** the commit that completes a step is the commit that ticks its box and updates the status line above. It is never more than one commit out of date.

---

## Steps

- [x] **0 — Documentation foundation.** `README.md` (targets, API, architecture, declined scope) and this file.
- [x] **1 — Scaffold.** Maven, Spring Boot 4, Java 21, Docker Compose with Postgres, Flyway baseline, health check responding.
- [x] **2 — Domain, test-first.** ShortCode generation, TargetUrl and SSRF validation, Alias rules and reserved words, expiry evaluation. Pure JUnit, no Spring, no database. Red → green → refactor, visible in the history.
- [x] **3 — Persistence.** Spring Data JDBC adapter, unique index on ShortCode, Testcontainers integration tests including the collision-retry path.
- [x] **4 — API.** Create, resolve, get, deactivate. MockMvc contract tests written first. `problem+json` mapping, API key filter, ownership check on delete.
- [x] **5 — Cache.** Caffeine behind the port: TTL clamped to `min(60s, time-to-expiry)`, negative caching, bounded size, eviction on write. Test that a deactivated ShortLink stops resolving locally.
- [x] **6 — Clicks.** LongAdder registry, scheduled batch flush, shutdown hook. Test that N Resolutions produce N Clicks once flushed.
- [x] **7 — Operability.** Structured JSON logging with request IDs, Micrometer counters and timers, OpenAPI, GitHub Actions CI.
- [ ] **8 — Docs close-out.** Reconcile the README against what was actually built. Flip ADRs 0001–0004 from `status: proposed` to `accepted` — or rewrite any that did not survive contact with the code, which makes for a better record than one that was right first time. Fill in **Cuts** below.
- [ ] **9 — Load test.** k6 against the redirect path; record the real p99 in the README, whatever it says.

---

## Cut line

Time budget is roughly one focused day. If it runs short, **cut scope, never polish** — a smaller service that is exhaustively tested and cleanly operable is worth more than a feature-complete one with thin tests.

Pre-committed drop order:

1. k6 load test (step 9)
2. Custom Aliases
3. Click counting

Anything dropped gets an entry below and a paragraph in the README's *Deliberately not built* section describing how it would be done.

## Cuts

*Nothing cut yet. Recorded here as it happens, in the commit where the decision is made.*

---

## Roadmap

Ideas that don't fit the day but are worth doing next — additional static
analysis, CI enforcement, and other production hardening — are tracked
separately in [`ROADMAP.md`](./ROADMAP.md) rather than here, so this file stays
scoped to build order.
