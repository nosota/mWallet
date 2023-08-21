ALTER TABLE transaction
    RENAME COLUMN hold_timestamp TO hold_reserve_timestamp;
ALTER TABLE transaction_snapshot
    RENAME COLUMN hold_timestamp TO hold_reserve_timestamp;
ALTER TABLE transaction_snapshot_archive
    RENAME COLUMN hold_timestamp TO hold_reserve_timestamp;
