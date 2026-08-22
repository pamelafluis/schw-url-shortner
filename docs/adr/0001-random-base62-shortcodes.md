---
status: accepted
---

# Random base62 ShortCodes, not a sequence

ShortCodes are seven characters drawn randomly from a base62 alphabet, with the database's unique index acting as the collision authority and the writer retrying up to three times on conflict. The obvious alternative — encoding a monotonic database sequence — is collision-free by construction and was rejected anyway, because sequential codes are **enumerable**: anyone can walk `1, 2, 3...` and harvest every TargetUrl the service hosts, which for a link shortener is a privacy incident rather than a performance concern. A seven-character space is ~3.5 × 10^12, so at the stated 100M-link target the birthday-collision rate stays low enough that retry-on-conflict is a rare path rather than a hot one.

## Consequences

Writes carry a small probability of retry, and the uniqueness guarantee lives in the schema (the unique index) rather than in application code — so that index is load-bearing and must never be dropped. If the write path ever needed to be coordination-free and guaranteed-unique, the migration is to lease counter ranges per instance and encode within a lease, not to reintroduce a shared global sequence.
