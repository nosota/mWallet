CREATE TABLE wallet
(
    id         SERIAL PRIMARY KEY,
    type       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wallet_type ON wallet (type);

CREATE TABLE transaction
(
    id                       SERIAL PRIMARY KEY,
    wallet_id                INTEGER REFERENCES wallet (id),
    amount                   BIGINT      NOT NULL,
    type                     VARCHAR(32) NOT NULL,
    status                   VARCHAR(32) NOT NULL,
    hold_timestamp           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirm_reject_timestamp TIMESTAMP,
    description              TEXT
);
CREATE INDEX idx_transaction_type ON transaction (type);
CREATE INDEX idx_transaction_status ON transaction (status);

CREATE TABLE transaction_snapshot
(
    id                       SERIAL PRIMARY KEY,
    wallet_id                INTEGER     NOT NULL REFERENCES wallet (id),
    type                     VARCHAR(50) NOT NULL, -- like 'DEBIT' or 'CREDIT'
    amount                   BIGINT      NOT NULL,
    status                   VARCHAR(50) NOT NULL, -- like 'HOLD', 'CONFIRMED', or 'REJECTED'
    hold_timestamp           TIMESTAMP,
    confirm_reject_timestamp TIMESTAMP,
    snapshot_date            TIMESTAMP   NOT NULL,
    CONSTRAINT fk_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id)
);
CREATE INDEX idx_transaction_snapshot_type ON transaction_snapshot (type);
CREATE INDEX idx_transaction_snapshot_status ON transaction_snapshot (status);
CREATE INDEX idx_transaction_snapshot_date ON transaction_snapshot (snapshot_date);
