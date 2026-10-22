-- V12: Prevent multiple active subscriptions per Business Owner

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_one_active_per_business
ON subscriptions (business_id)
WHERE status = 'ACTIVE';