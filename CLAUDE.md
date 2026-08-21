# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A URL shortener service (Spring Boot 4.1.1 / Java 21 / Postgres). Full requirements, API contract, and architecture are documented in [`README.md`](./README.md) — read it before making non-trivial changes, don't duplicate it here. Domain vocabulary (ShortLink, ShortCode, Alias, TargetUrl, Resolution, Click, Deactivation) is fixed by [`CONTEXT.md`](./CONTEXT.md); use those exact terms in code, commits, and discussion, and avoid the synonyms it lists (e.g. never "slug"/"hash" for ShortCode, never "hit"/"visit" for Resolution).

See the pre-committed drop order in `docs/PLAN.md`. Four ADRs in `docs/adr/` record the load-bearing design decisions (random base62 codes over sequences, 302 over 301, approximate click counting, Postgres behind a bounded-staleness cache) — read the relevant one before touching code in that area, since each documents alternatives already considered and rejected.

## Commands

```bash
docker compose up              # app + Postgres, full stack
./mvnw verify                  # full test suite (unit + Testcontainers integration)
./mvnw verify -Dtest=UrlShortenerApplicationTests#healthCheckRespondsUp   # single test
./mvnw spring-boot:run         # app only, needs Postgres already reachable (see application.yml env vars)
```

Integration tests spin up their own Postgres via Testcontainers — a running Docker daemon is the only prerequisite, no external database needed for `./mvnw verify`.

## Build order discipline

`docs/PLAN.md` tracks the step-by-step build order and is the source of truth for what's implemented vs. planned. **The commit that completes a step is the commit that ticks that step's checkbox and updates the status line in `docs/PLAN.md` — never leave it more than one commit stale.** If a step's scope changes or gets cut, record it under the "Cuts" section in the same commit, with a corresponding note in the README's "Deliberately not built" section.

## Architecture

Ports and adapters, packaged by feature (not by layer — no `controller/service/repository` split):

```
link/domain      ← ShortLink, ShortCode, TargetUrl, code generation,
                   expiry rules, alias + SSRF validation.
                   Pure Java. Zero Spring imports — this is what keeps
                   the domain tests fast (milliseconds, no Spring context).
link/api         ← REST adapter, problem+json (RFC 9457) mapping, API key filter
link/persistence ← Postgres adapter (Spring Data JDBC), Flyway migrations
link/cache       ← Caffeine adapter, behind a port (see ADR-0004)
shared/          ← request IDs, error plumbing
```

As of this writing only the scaffold exists (`UrlShortenerApplication`, `application.yml`, the Flyway baseline migration, the actuator health check) — the `link/*` packages above are the target layout for the domain/persistence/API/cache steps in `docs/PLAN.md`, not yet present.

**The resolution path** (`GET /{code}`) is the hot path and the one perf targets in the README are about:

```
GET /{code}
  → Caffeine (hits AND misses cached — negative caching prevents random-ShortCode scans from becoming a DB DoS)
      hit  → check expiry/active → 302, buffer a Click
      miss → Postgres → populate cache → 302, buffer a Click
  → 404 if never issued, 410 if expired or deactivated
```

Clicks never touch the database on the request path — they accumulate in memory (planned: `LongAdder` registry) and flush on a timer, which is why Resolution and Click are deliberately distinct concepts (see CONTEXT.md) with a stated, bounded loss budget (ADR-0003).

Testing follows the same domain/adapter split: domain logic gets plain JUnit with no Spring and no database, written test-first; API contract tests use MockMvc written before the controllers; persistence/integration tests use Testcontainers Postgres, including the ShortCode collision-retry path that ADR-0001's uniqueness guarantee depends on.