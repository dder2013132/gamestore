-- Existing orders keep a NULL token. Repeated checkout tokens are unique per member.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS checkout_token UUID;
CREATE UNIQUE INDEX IF NOT EXISTS orders_user_checkout_token_idx ON orders (user_id, checkout_token);
