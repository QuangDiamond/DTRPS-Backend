
-- V13: Ensure payment renewal belongs to the correct subscription

-- PostgreSQL requires a UNIQUE constraint on the referenced column pair
ALTER TABLE subscription_renewal_windows
ADD CONSTRAINT uq_renewal_subscription
UNIQUE (renewal_id, subscription_id);

-- Ensure payment and renewal reference the same subscription
ALTER TABLE payments
ADD CONSTRAINT fk_payment_renewal_subscription
FOREIGN KEY (renewal_id, subscription_id)
REFERENCES subscription_renewal_windows (renewal_id, subscription_id);
