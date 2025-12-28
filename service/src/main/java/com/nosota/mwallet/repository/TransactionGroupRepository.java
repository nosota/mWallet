package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionGroupRepository extends JpaRepository<TransactionGroup, UUID> {
    List<TransactionGroup> findAllByStatusIn(List<TransactionGroupStatus> statuses);
}
