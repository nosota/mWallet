package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.LedgerEntriesTracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTrackingRepository extends JpaRepository<LedgerEntriesTracking, Integer> {
}
