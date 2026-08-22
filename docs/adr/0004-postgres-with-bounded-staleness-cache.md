---
status: accepted
---

# Postgres behind an in-process cache, with bounded staleness

ShortLinks are stored in Postgres and read through an in-process Caffeine cache held behind a port. A key-value store is the better shape for this access pattern — DynamoDB especially — but it buys cloud setup and lock-in that this deployment does not need, while Postgres gives transactional writes and the unique index that ADR-0001 depends on for collision detection. Redis was rejected as the system of record on durability grounds, and deferred as a cache because a single node does not need it yet.

The cache holds both hits and misses (negative caching, so scanning random ShortCodes cannot turn the miss path into a database denial-of-service), is bounded at roughly 100k entries so it cannot become a memory leak, and expires entries at `min(60s, time until the ShortLink expires)` so a cached entry can never outlive its own ShortLink.

## Consequences

The cache is per-instance, so a Deactivation performed on one node is only guaranteed to take effect fleet-wide after the cache TTL. That is published as an SLO — **Deactivation takes effect within 60 seconds** — rather than hidden: it is the price of not running Redis. Because the cache sits behind a port, moving to Redis with pub/sub invalidation (which reduces that window to milliseconds) is an adapter swap and a config change, not a rewrite.
