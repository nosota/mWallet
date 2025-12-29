-- V2.12: Add audit trail fields to track who initiated operations
-- Purpose: Enhanced audit trail for compliance and fraud detection

-- =====================================================================
-- 1. Add audit fields to transaction table
-- =====================================================================
ALTER TABLE transaction
    ADD COLUMN initiated_by_user_id BIGINT,
    ADD COLUMN initiator_type VARCHAR(10),
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(500);

COMMENT ON COLUMN transaction.initiated_by_user_id IS 'ID of the user who initiated this transaction. USER/MERCHANT/ADMIN: user ID, SYSTEM: null';

COMMENT ON COLUMN transaction.initiator_type IS
    'Type of entity that initiated this transaction: USER, MERCHANT, ADMIN, SYSTEM';

COMMENT ON COLUMN transaction.ip_address IS
    'IP address from which the transaction was initiated (for fraud detection)';

COMMENT ON COLUMN transaction.user_agent IS
    'User agent string from the client (for audit trail)';

CREATE INDEX idx_transaction_initiated_by
    ON transaction(initiated_by_user_id, initiator_type);

-- =====================================================================
-- 2. Add audit fields to settlement table
-- =====================================================================
ALTER TABLE settlement
    ADD COLUMN triggered_by_user_id BIGINT,
    ADD COLUMN triggered_by_type VARCHAR(10),
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(500);

COMMENT ON COLUMN settlement.triggered_by_user_id IS 'ID of the user who triggered this settlement. ADMIN: admin user ID, SYSTEM (scheduled): null';

COMMENT ON COLUMN settlement.triggered_by_type IS
    'Type of entity that triggered this settlement: ADMIN, SYSTEM';

COMMENT ON COLUMN settlement.ip_address IS
    'IP address from which the settlement was triggered';

COMMENT ON COLUMN settlement.user_agent IS
    'User agent string from the client that triggered the settlement';

CREATE INDEX idx_settlement_triggered_by
    ON settlement(triggered_by_user_id, triggered_by_type);

-- =====================================================================
-- 3. Add audit fields to refund table
-- =====================================================================
ALTER TABLE refund
    ADD COLUMN initiated_by_user_id BIGINT,
    ADD COLUMN initiator_type VARCHAR(10),
    ADD COLUMN ip_address VARCHAR(45),
    ADD COLUMN user_agent VARCHAR(500);

COMMENT ON COLUMN refund.initiated_by_user_id IS 'ID of the user who initiated this refund. MERCHANT: merchant user ID, ADMIN: admin user ID, SYSTEM: null';

COMMENT ON COLUMN refund.initiator_type IS 'Type of entity that initiated this refund: MERCHANT, ADMIN, SYSTEM. Note: Separate from initiator field which provides business-level context.';

COMMENT ON COLUMN refund.ip_address IS
    'IP address from which the refund was initiated';

COMMENT ON COLUMN refund.user_agent IS
    'User agent string from the client that initiated the refund';

CREATE INDEX idx_refund_initiated_by
    ON refund(initiated_by_user_id, initiator_type);

-- =====================================================================
-- 4. Notes on audit trail implementation
-- =====================================================================
-- IMPORTANT: Audit trail usage guidelines:
--
-- InitiatorType values:
-- - USER: Regular user (buyer) operation
-- - MERCHANT: Merchant operation
-- - ADMIN: Platform administrator operation
-- - SYSTEM: Automated system operation (scheduled jobs, webhooks)
--
-- Fields usage:
-- - initiated_by_user_id: Null for SYSTEM, user ID for USER/MERCHANT/ADMIN
-- - initiator_type: Always populated (USER/MERCHANT/ADMIN/SYSTEM)
-- - ip_address: IPv4 or IPv6 address (45 chars sufficient for IPv6)
-- - user_agent: Browser/client identification string
--
-- Privacy considerations:
-- - IP addresses may be considered PII in some jurisdictions
-- - Implement data retention policies
-- - Consider GDPR/CCPA compliance for audit data
--
-- Fraud detection:
-- - Track unusual IP patterns
-- - Monitor user agent anomalies
-- - Detect account takeover attempts
-- - Identify bot activity
