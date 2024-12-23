package com.nosota.mwallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LedgerEntriesTracking
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ledger_entry_id", nullable = false)
    private Integer ledgerEntryId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    // Constructor for two fields: ledgerEntryId and referenceId
    public LedgerEntriesTracking(Integer ledgerEntryId, UUID referenceId) {
        this.ledgerEntryId = ledgerEntryId;
        this.referenceId = referenceId;
    }
}
