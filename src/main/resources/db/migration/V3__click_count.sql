-- Populated by the Click registry's scheduled flush (ADR-0003), applied as a
-- batched increment (click_count = click_count + delta), never an overwrite.
ALTER TABLE short_link ADD COLUMN click_count BIGINT NOT NULL DEFAULT 0;
