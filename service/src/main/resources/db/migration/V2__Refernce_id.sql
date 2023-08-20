CREATE TABLE transaction_group
(
    id         UUID PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

ALTER TABLE transaction
    ADD COLUMN reference_id UUID,
    ADD FOREIGN KEY (reference_id) REFERENCES transaction_group(id);

