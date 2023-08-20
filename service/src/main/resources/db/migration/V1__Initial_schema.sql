CREATE TABLE wallet
(
    id         SERIAL PRIMARY KEY,
    type       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_wallet_type ON wallet (type);

CREATE TABLE transaction
(
    id               SERIAL PRIMARY KEY,
    wallet_id        INTEGER REFERENCES wallet (id),
    amount           BIGINT NOT NULL,
    type             VARCHAR(32) NOT NULL,
    status           VARCHAR(32) NOT NULL,
    transaction_date TIMESTAMPTZ DEFAULT NOW(),
    description      TEXT
);
CREATE INDEX idx_transaction_type ON transaction (type);
CREATE INDEX idx_transaction_status ON transaction (status);


CREATE TABLE wallet_balance
(
    id            SERIAL PRIMARY KEY,
    wallet_id     INTEGER REFERENCES wallet (id) NOT NULL,
    balance       BIGINT DEFAULT 0,
    snapshot_date TIMESTAMPTZ    DEFAULT NOW()
);

-- Index to speed up balance fetching
CREATE INDEX idx_wallet_balances_wallet_id_snapshot_date ON wallet_balance (wallet_id, snapshot_date);
