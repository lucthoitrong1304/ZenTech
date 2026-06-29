-- Backfill online orders that were paid successfully before payment callbacks
-- started auto-confirming CREATED orders.
UPDATE orders
SET order_status = 'CONFIRMED'
WHERE payment_method IN ('VNPAY', 'MOMO')
  AND payment_status = 'SUCCESS'
  AND order_status = 'CREATED';
