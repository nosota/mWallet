-- While using an archive table without foreign keys has its advantages, mainly in flexibility
-- and performance (no need to check for FK constraints during archiving), it does put the onus
-- on your application and maintenance processes to ensure data consistency.
CREATE TABLE transaction_snapshot_archive
    AS SELECT * FROM transaction_snapshot WHERE false;

CREATE INDEX idx_transaction_snapshot_archive_type ON transaction_snapshot_archive (type);
CREATE INDEX idx_transaction_snapshot_archive_status ON transaction_snapshot_archive (status);
CREATE INDEX idx_transaction_snapshot_archive_date ON transaction_snapshot_archive (snapshot_date);

ALTER TABLE transaction_snapshot
    ADD COLUMN is_ledger_entry BOOLEAN DEFAULT FALSE;
