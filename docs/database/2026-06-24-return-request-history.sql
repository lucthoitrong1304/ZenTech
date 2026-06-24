-- Allow an order to keep multiple return attempts after previous requests were rejected.
-- Keep a regular index for the foreign key before removing the generated unique index.
CREATE INDEX idx_return_requests_order_id ON return_requests (order_id);

ALTER TABLE return_requests
    DROP INDEX UKmlyu5yxuty6xy4bndr5rrnfr0;
