package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.WalletOwnerDTO;
import com.nosota.mwallet.dto.WalletOwnerMapper;
import com.nosota.mwallet.model.OwnerType;
import com.nosota.mwallet.model.WalletOwner;
import com.nosota.mwallet.repository.WalletOwnerRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;

@Service
@Validated
@AllArgsConstructor
public class WalletOwnershipService {

    private final WalletOwnerRepository walletOwnerRepository;

    /**
     * Assigns ownership to a specific wallet.
     *
     * @param walletId   the wallet ID to which ownership should be assigned.
     * @param ownerType  the type of the owner.
     * @param ownerRef   the reference of the owner.
     * @return WalletOwner entity after saving.
     */
    @Transactional
    public WalletOwnerDTO assignOwnership(@NotNull Integer walletId, @NotNull OwnerType ownerType, @NotBlank String ownerRef) {

        // Check if a record already exists for the wallet
        Optional<WalletOwner> existingOwnerOpt = walletOwnerRepository.findByWalletId(walletId);

        WalletOwner walletOwner;
        if (existingOwnerOpt.isPresent()) {
            walletOwner = existingOwnerOpt.get();
        } else {
            walletOwner = new WalletOwner();
            walletOwner.setWalletId(walletId);
        }
        walletOwner.setOwnerType(ownerType);
        walletOwner.setOwnerRef(ownerRef);

        // Save the entity
        WalletOwner savedWalletOwner = walletOwnerRepository.save(walletOwner);

        // Convert to DTO and return
        return WalletOwnerMapper.INSTANCE.toDTO(savedWalletOwner);
    }

    /**
     * Retrieves the owner reference associated with the specified wallet ID.
     * <p>
     * This method fetches the {@link WalletOwner} record associated with the given wallet ID
     * and returns the owner reference string. If no such record exists for the provided
     * wallet ID, an empty {@link Optional} is returned.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet whose owner reference needs to be fetched.
     * @return An {@link Optional} containing the owner reference string if found; otherwise, an empty {@link Optional}.
     */
    @Transactional
    public Optional<String> findOwnerRefByWalletId(@NotNull Integer walletId) {
        return walletOwnerRepository.findByWalletId(walletId)
                .map(WalletOwner::getOwnerRef);
    }
}
