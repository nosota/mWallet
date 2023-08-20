-- Dimension Tables

-- Wallet Dimension
CREATE TABLE dim_wallet
(
    wallet_id SERIAL PRIMARY KEY,
    type      VARCHAR(255) NOT NULL
);

-- Owner Dimension
CREATE TABLE dim_owner
(
    owner_id   SERIAL PRIMARY KEY,
    owner_type VARCHAR(16) NOT NULL,
    owner_ref  VARCHAR(32)
);

-- Transaction Group Dimension
CREATE TABLE dim_transaction_group
(
    transaction_group_id UUID PRIMARY KEY,
    status               VARCHAR(20) NOT NULL,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP
);

-- Date Dimension
CREATE TABLE dim_date
(
    date_id SERIAL PRIMARY KEY,
    date    DATE NOT NULL,
    month   INT  NOT NULL,
    quarter INT  NOT NULL,
    year    INT  NOT NULL,
    UNIQUE (date)
);

-- Type Dimension
CREATE TABLE dim_type
(
    type_id          SERIAL PRIMARY KEY,
    transaction_type VARCHAR(32) NOT NULL
);

-- Status Dimension
CREATE TABLE dim_status
(
    status_id          SERIAL PRIMARY KEY,
    transaction_status VARCHAR(32) NOT NULL
);

-- Fact Table
CREATE TABLE fact_transaction
(
    transaction_id           SERIAL PRIMARY KEY,
    wallet_dim_id            INT REFERENCES dim_wallet (wallet_id),
    owner_dim_id             INT REFERENCES dim_owner (owner_id),
    transaction_group_dim_id UUID REFERENCES dim_transaction_group (transaction_group_id),
    date_dim_id              INT REFERENCES dim_date (date_id),
    type_dim_id              INT REFERENCES dim_type (type_id),
    status_dim_id            INT REFERENCES dim_status (status_id),
    amount                   BIGINT NOT NULL
);
