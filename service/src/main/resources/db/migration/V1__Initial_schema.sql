CREATE TABLE wallets
(
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE transactions
(
    id               SERIAL PRIMARY KEY,
    wallet_id        INTEGER REFERENCES wallets (id),
    amount           NUMERIC(20, 2) NOT NULL,
    transaction_date TIMESTAMPTZ DEFAULT NOW(),
    description      TEXT
);

CREATE TABLE wallet_balances
(
    id            SERIAL PRIMARY KEY,
    wallet_id     INTEGER REFERENCES wallets (id) NOT NULL,
    balance       NUMERIC(20, 2) DEFAULT 0,
    snapshot_date TIMESTAMPTZ    DEFAULT NOW()
);

-- Index to speed up balance fetching
CREATE INDEX idx_wallet_balances_wallet_id_snapshot_date ON wallet_balances (wallet_id, snapshot_date);
