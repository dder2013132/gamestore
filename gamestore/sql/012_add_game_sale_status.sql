-- Run before deploying game editing and sale suspension. Existing games stay on sale.
ALTER TABLE games ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
