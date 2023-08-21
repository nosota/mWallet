CREATE TABLE transaction_group
(
    id         UUID PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_transaction_group_status ON transaction_group (status);

ALTER TABLE transaction
    ADD COLUMN reference_id UUID,
    ADD FOREIGN KEY (reference_id) REFERENCES transaction_group(id);

ALTER TABLE transaction_snapshot
    ADD COLUMN reference_id UUID,
    ADD FOREIGN KEY (reference_id) REFERENCES transaction_group(id);
