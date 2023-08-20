CREATE TABLE wallet_owner
(
    id         SERIAL PRIMARY KEY,
    wallet_id  INT NOT NULL,
    owner_type VARCHAR(16) NOT NULL, -- 'USER', 'SYSTEM'
    owner_ref  VARCHAR(32),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (wallet_id) REFERENCES wallet (id) ON DELETE CASCADE
);
