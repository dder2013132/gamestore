-- One atomic statement: existing orders remain completed; new orders await a demo payment.
DO $$
BEGIN
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR(12) NOT NULL DEFAULT 'COMPLETED';
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
    ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
    UPDATE orders SET completed_at = COALESCE(created_at, CURRENT_TIMESTAMP)
        WHERE status = 'COMPLETED' AND completed_at IS NULL;
    ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'PENDING';
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'orders'::regclass AND conname = 'orders_status_valid') THEN
        ALTER TABLE orders ADD CONSTRAINT orders_status_valid CHECK (
            (status = 'PENDING' AND completed_at IS NULL AND cancelled_at IS NULL) OR
            (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL) OR
            (status = 'CANCELLED' AND completed_at IS NULL AND cancelled_at IS NOT NULL));
    END IF;
END $$;
