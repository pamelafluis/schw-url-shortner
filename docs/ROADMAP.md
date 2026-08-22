# Roadmap

Ideas that don't fit the ~1 day budget for this take-home, but that a continued,
production-lived version of this service would take on next. This is distinct
from two other lists already in this repo: the README's [*Deliberately not
built*](../README.md#deliberately-not-built) section covers scope explicitly
declined as out of bounds for the exercise, and `PLAN.md`'s
[*Cuts*](./PLAN.md#cuts) records scope actually dropped mid-build to make the
day fit. Everything here is instead scope that's in bounds conceptually, just
not worth the time this round.

---

## Static analysis beyond formatting

Spotless (`google-java-format`) is wired into `./mvnw verify` now — it settles
formatting arguments for free and needs no tuning. The following go further
than formatting into correctness and would be worth adding for a codebase with
a longer lifespan than a day:

- **Error Prone.** Compile-time bug-pattern checks (e.g. mismatched equals/hashCode,
  unused return values from immutable types, `Optional` misuse). Highest
  signal-to-noise of the three, but adds an annotation-processor hook to the
  compiler plugin and a bit of build-time cost — worth it once the codebase is
  bigger than a handful of packages.
- **PMD.** Broader rule-set (unused code, overly complex methods, some
  security-adjacent rules). Needs a curated ruleset file to avoid drowning in
  low-value warnings on day one; not worth authoring that file for a
  single-day build.
- **Checkstyle.** Style/convention enforcement past what a formatter covers —
  import ordering rules, Javadoc-on-public-API requirements, naming
  conventions. Same tradeoff as PMD: valuable once there's a team and a style
  guide to encode, not before.

## Other things a longer build would take on

- **CI enforcement.** Once step 7's GitHub Actions workflow exists, add
  `spotless:check` (and, later, Error Prone/PMD/Checkstyle) as a required
  status check, not just a local `./mvnw verify` habit.
- **Load test follow-through.** Step 9's k6 run is a one-shot snapshot; a
  longer-lived service would run it in CI against a fixed baseline and fail
  on regression, not just record a p99 once in the README.
- **The scope in README's Deliberately not built.** User accounts, per-click
  analytics, click dedup, idempotency keys, malicious-URL scanning, and a
  Redis/multi-node cache are all still there if this ever needed to grow past
  a take-home — see that section for the reasoning on each.
