-- Run once after 006_create_users.sql, before deploying the JDBC order code.
-- Existing orders must be empty, or have their owners assigned before SET NOT NULL.
BEGIN;

ALTER TABLE orders
    ADD COLUMN user_id BIGINT NOT NULL REFERENCES users(user_id);

CREATE INDEX orders_user_created_idx ON orders (user_id, created_at DESC, order_id DESC);

COMMIT;
