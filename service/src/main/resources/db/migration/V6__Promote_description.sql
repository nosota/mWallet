ALTER TABLE transaction_snapshot
    ADD COLUMN description TEXT;

ALTER TABLE transaction_snapshot_archive
    ADD COLUMN description TEXT;
