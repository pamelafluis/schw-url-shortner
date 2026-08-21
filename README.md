# URL Shortener

A service that issues short, opaque codes standing in for longer destinations, and resolves those codes back into redirects.

Links can carry a caller-chosen alias, expire on a schedule, and be deactivated; resolutions are counted. The vocabulary used throughout the code is defined in [`CONTEXT.md`](./CONTEXT.md) — **ShortLink**, **ShortCode**, **Alias**, **TargetUrl**, **Resolution**, **Click** — and those words mean exactly what that file says they mean.

> **Status:** under construction. See [`docs/PLAN.md`](./docs/PLAN.md) for the build order and what is done so far.

---

## Running it

```bash
docker compose up          # app + Postgres
```

Then, end to end:

```bash
# create a ShortLink
curl -s -X POST localhost:8080/api/v1/links \
  -H 'X-API-Key: dev-key' -H 'Content-Type: application/json' \
  -d '{"targetUrl":"https://example.com/some/long/path"}'
# → 201 {"code":"aB3xK9","shortUrl":"http://localhost:8080/aB3xK9", ...}

# resolve it
curl -i localhost:8080/aB3xK9
# → 302 Location: https://example.com/some/long/path
#   Cache-Control: no-store

# deactivate it
curl -X DELETE localhost:8080/api/v1/links/aB3xK9 -H 'X-API-Key: dev-key'
curl -i localhost:8080/aB3xK9
# → 410 Gone
```

Tests: `./mvnw verify`. Integration tests start their own Postgres via Testcontainers, so a running Docker daemon is the only prerequisite.

---

## Stated targets

This service is **designed** for the numbers below and **shipped** as a single node. That distinction is deliberate: nothing in the code blocks horizontal scaling (the app is stateless apart from the click buffer, there is no in-process sequence generator, and reads are cache-first), but running a multi-node fleet was out of budget and would have been undemonstrated complexity.

| | Target |
|---|---|
| Read:write ratio | ≈ 100:1 |
| Redirect latency | p99 < 50 ms |
| Corpus size | 100M ShortLinks |
| Redirect throughput ceiling | 10k/s |
| Deactivation propagation | ≤ 60 s fleet-wide |
| Click count durability | ≤ 5 s of counts lost on hard crash |

The last two are *guarantees, not defects* — they are the stated consequences of decisions [0003](./docs/adr/0003-approximate-click-counting.md) and [0004](./docs/adr/0004-postgres-with-bounded-staleness-cache.md).

A key-value store is honestly the better shape for this workload — DynamoDB in particular — and the reasoning for using Postgres anyway is in ADR-0004.

---

## API

| Method | Path | Purpose | Auth |
|---|---|---|---|
| `POST` | `/api/v1/links` | Create a ShortLink | API key |
| `GET` | `/api/v1/links/{code}` | Metadata + Click count | API key |
| `DELETE` | `/api/v1/links/{code}` | Deactivate (soft) | API key + ownership |
| `GET` | `/{code}` | Resolve → redirect | none |

**Create**

```json
POST /api/v1/links
{ "targetUrl": "https://example.com/x", "alias": "launch", "expiresAt": "2026-12-31T23:59:59Z" }
```

`alias` and `expiresAt` are optional. Omit `alias` and a ShortCode is generated.

**Errors** follow RFC 9457 `application/problem+json`:

```json
{
  "type": "https://example.com/probs/alias-taken",
  "title": "Alias already in use",
  "status": 409,
  "detail": "The alias 'launch' is already taken.",
  "instance": "/api/v1/links"
}
```

| Status | When |
|---|---|
| `400` | Malformed request, or a TargetUrl that fails validation (see below) |
| `401` | Missing or unknown API key |
| `403` | Deactivating a ShortLink belonging to another API key |
| `404` | ShortCode was never issued |
| `409` | Requested Alias is taken, or is a reserved word |
| `410` | ShortLink is expired or deactivated |

The `404`/`410` split is intentional: `410` says "this existed and is gone", which is what a former audience of the link deserves to be told.

### Alias rules

Aliases occupy the **same uniqueness namespace** as generated ShortCodes — they must be able to collide with each other, or a caller could claim an alias that a future generated code would duplicate.

- `[A-Za-z0-9_-]`, 3–32 characters, case-sensitive
- Reserved and rejected: `api`, `health`, `actuator`, `metrics`, `docs`, `robots.txt`, `favicon.ico` — nobody gets to shadow the service's own routes
- Already taken → `409`

### TargetUrl validation

Rejected at creation time:

- Any scheme other than `http`/`https` (blocks `javascript:`, `data:`, `file:`)
- `localhost`, loopback, private ranges, and link-local — notably `169.254.169.254`, the cloud instance-metadata address
- The service's own host, which would otherwise let callers build redirect chains and loops

A shortener that will redirect to anything it is given is a server-side request forgery vector, not a feature.

---

## Architecture

Ports and adapters, packaged by feature:

```
link/domain      ← ShortLink, ShortCode, TargetUrl, code generation,
                   expiry rules, alias + SSRF validation.
                   Pure Java. Zero Spring imports.
link/api         ← REST adapter, problem+json mapping, API key filter
link/persistence ← Postgres adapter (Spring Data JDBC), Flyway migrations
link/cache       ← Caffeine adapter, behind a port
shared/          ← request IDs, error plumbing
```

The payoff is testing speed, not tidiness: every interesting rule — code generation, expiry evaluation, URL validation, alias legality — is a pure function testable with no Spring context and no database, so the tests that matter run in milliseconds.

Separate Maven modules were rejected as over-engineering for a single bounded context; a `controller/service/repository` split was rejected as structure that carries no information.

**The Resolution path**, which is the hot one:

```
GET /{code}
  → Caffeine (hits and misses both cached)
      hit  → check expiry/active → 302, buffer a Click
      miss → Postgres → populate cache → 302, buffer a Click
  → 404 if never issued, 410 if expired or deactivated
```

Clicks never touch the database on the request path; they accumulate in memory and flush on a timer.

---

## Design decisions

Four decisions carry real consequence, and each has a record with the alternatives that were rejected and why:

- **[ADR-0001](./docs/adr/0001-random-base62-shortcodes.md)** — random base62 ShortCodes rather than an encoded sequence. The argument is enumerability, not collision math.
- **[ADR-0002](./docs/adr/0002-302-not-301.md)** — `302`, not `301`. Supporting expiry and deactivation makes a permanent redirect untenable.
- **[ADR-0003](./docs/adr/0003-approximate-click-counting.md)** — Clicks are approximate and eventually consistent, with a stated loss budget.
- **[ADR-0004](./docs/adr/0004-postgres-with-bounded-staleness-cache.md)** — Postgres behind a bounded-staleness in-process cache.

---

## Deliberately not built

Each of these was considered and declined; none is an oversight.

- **User accounts and authentication.** An API key per caller gives multi-tenancy, ownership checks, and a rate-limiting key for a fraction of the cost. Full accounts are a well-understood problem that would have consumed a large share of the budget while demonstrating nothing about the interesting parts of this system. Adding them means a users table, password hashing, session or token issuance, and ownership migrating from API key to user id.
- **Per-click referrer, user-agent, and geo analytics.** Turns each Resolution into a row rather than an increment, which changes the storage story entirely. It would be a `click_events` table (or a stream) with rollups, not a counter column.
- **URL deduplication.** Returning an existing ShortCode for a repeat TargetUrl would save storage, but a ShortLink is the aggregate, not the URL: two links to the same destination legitimately have different owners, lifetimes, and Click counts, and sharing one would leak one caller's link to another.
- **`Idempotency-Key` on create.** A retried create producing a second working ShortCode is harmless here, so the key store and its expiry logic earn nothing.
- **Malicious URL scanning.** Real abuse prevention means a reputation feed (Safe Browsing or similar) checked asynchronously after creation, plus a takedown path. The SSRF validation above is a different thing and *is* implemented.
- **Redis, and multi-node deployment.** The cache sits behind a port precisely so this is an adapter swap; see ADR-0004.

---

## Known limits

- **Deactivation is not instant across a fleet.** With a per-instance cache, a link deactivated on one node can keep resolving on another for up to 60 seconds. Single-node deployment makes this unobservable today; Redis pub/sub invalidation reduces it to milliseconds.
- **Click counts lag and can be lost.** Up to 5 seconds' worth on an ungraceful shutdown. Clicks are an analytics signal, not a ledger.
- **Resolutions ≠ Clicks by design.** Caching means the service cannot see traffic it never handles, and the vocabulary keeps the two ideas separate so the gap is discussable rather than accidental.
- **Single node.** No load balancer, no replication, no failover.

---

## Testing

| Layer | Approach |
|---|---|
| Domain | Plain JUnit, no Spring, no database. Written test-first. |
| API contract | MockMvc, written before the controllers. |
| Persistence & integration | Testcontainers Postgres, including the ShortCode collision-retry path. |
| Load | k6 against the redirect path, to produce the p99 figure rather than assert it. |

Measured latency figures will be recorded here once the load test has been run.
