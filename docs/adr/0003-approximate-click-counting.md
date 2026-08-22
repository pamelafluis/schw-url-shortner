---
status: accepted
---

# Clicks are approximate and eventually consistent

Clicks are accumulated in-process per ShortCode and flushed to the database as a batched update every five seconds, plus once on graceful shutdown. Incrementing a row on every Resolution would be exact, but it puts a database write on every read and discards the 100:1 read-to-write ratio the whole design is built around; a durable queue with an aggregating consumer is the correct answer at the stated 10k/s ceiling and is disproportionate infrastructure for the current deployment.

The resulting guarantee is stated as a requirement, not apologised for as a caveat: **a Click count may lag a Resolution by up to five seconds, and a hard crash loses at most five seconds of counts.** Clicks are an analytics signal, not a ledger.

## Consequences

`GET /api/v1/links/{code}` can report a count lower than the number of Resolutions that have actually occurred, and this is correct behaviour. Because counts live in process memory between flushes, the counter is the one piece of per-instance state in an otherwise stateless service — running multiple instances is safe (each flushes its own deltas as increments) but a lost instance loses its unflushed window. If Clicks ever became billable, the migration is to publish each Resolution to a durable log and aggregate downstream.
