package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.WalletOwnerDTO;
import com.nosota.mwallet.dto.WalletOwnerMapper;
import com.nosota.mwallet.model.OwnerType;
import com.nosota.mwallet.model.WalletOwner;
import com.nosota.mwallet.repository.WalletOwnerRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WalletOwnershipService {

    @Autowired
    private WalletOwnerRepository walletOwnerRepository;

    /**
     * Assigns ownership to a specific wallet.
     *
     * @param walletId   the wallet ID to which ownership should be assigned.
     * @param ownerType  the type of the owner.
     * @param ownerRef   the reference of the owner.
     * @return WalletOwner entity after saving.
     */
    @Transactional
    public WalletOwnerDTO assignOwnership(Integer walletId, OwnerType ownerType, String ownerRef) {

        // Check if a record already exists for the wallet
        Optional<WalletOwner> existingOwnerOpt = walletOwnerRepository.findByWalletId(walletId);

        WalletOwner walletOwner;
        if (existingOwnerOpt.isPresent()) {
            walletOwner = existingOwnerOpt.get();
            walletOwner.setOwnerType(ownerType);
            walletOwner.setOwnerRef(ownerRef);
        } else {
            walletOwner = new WalletOwner();
            walletOwner.setWalletId(walletId);
            walletOwner.setOwnerType(ownerType);
            walletOwner.setOwnerRef(ownerRef);
        }

        // Save the entity
        WalletOwner savedWalletOwner = walletOwnerRepository.save(walletOwner);

        // Convert to DTO and return
        return WalletOwnerMapper.INSTANCE.toDTO(savedWalletOwner);
    }
}
