-- The table is structured to trace the relationship between a ledger entry and the entries
-- transferred to the archive table. This table serves primarily for auditing purposes; hence,
-- foreign keys are omitted to enhance performance.
CREATE TABLE ledger_entries_tracking
(
    id               SERIAL PRIMARY KEY,
    ledeger_entry_id INTEGER NOT NULL,
    reference_id     UUID
);
