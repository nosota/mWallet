package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TransactionGroupRepository extends JpaRepository<TransactionGroup, UUID> {
}
