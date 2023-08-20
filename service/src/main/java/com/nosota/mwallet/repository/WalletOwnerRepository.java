package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.WalletOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletOwnerRepository extends JpaRepository<WalletOwner, Integer> {
    Optional<WalletOwner> findByWalletId(Integer walletId);
    List<WalletOwner> findByOwnerRef(String ownerRef);
}
