-- Apply before deploying the backend change that writes RETURN_REQUESTED/RETURNED.
-- VARCHAR keeps the database schema compatible with future Java OrderStatus values.
ALTER TABLE orders
    MODIFY COLUMN order_status VARCHAR(32) NULL;
