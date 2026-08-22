-- The primary key doubles as the unique index ADR-0001's collision-retry
-- guarantee depends on: two ShortCodes (generated or Alias) can never
-- coexist. Never drop or weaken this constraint.
CREATE TABLE short_link (
    short_code VARCHAR(32)  PRIMARY KEY,
    target_url TEXT         NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ,
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);
