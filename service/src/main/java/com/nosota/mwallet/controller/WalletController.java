package com.nosota.mwallet.controller;

import com.nosota.mwallet.api.WalletApi;
import com.nosota.mwallet.api.request.CreateMerchantWalletRequest;
import com.nosota.mwallet.api.request.CreateUserWalletRequest;
import com.nosota.mwallet.api.response.WalletResponse;
import com.nosota.mwallet.model.OwnerType;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.repository.WalletRepository;
import com.nosota.mwallet.service.WalletManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for wallet management operations.
 *
 * <p>Implements {@link WalletApi} interface for:
 * <ul>
 *   <li>Creating USER wallets (owned by individual users)</li>
 *   <li>Creating MERCHANT wallets (owned by merchants)</li>
 *   <li>Retrieving wallet information</li>
 * </ul>
 *
 * <p><b>Security Note:</b> ESCROW and SYSTEM wallets cannot be created via this API.
 * They are created internally by the system as needed through WalletManagementService.
 */
@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
public class WalletController implements WalletApi {

    private final WalletManagementService walletManagementService;
    private final WalletRepository walletRepository;

    @Override
    public ResponseEntity<WalletResponse> createUserWallet(CreateUserWalletRequest request) {
        log.info("Creating USER wallet: ownerId={}, currency={}, initialBalance={}",
                request.ownerId(), request.currency(), request.initialBalance());

        Integer walletId;

        if (request.initialBalance() != null && request.initialBalance() > 0) {
            walletId = walletManagementService.createNewWalletWithBalance(
                    WalletType.USER,
                    request.description(),
                    request.initialBalance(),
                    request.ownerId(),
                    OwnerType.USER_OWNER
            );
        } else {
            walletId = walletManagementService.createNewWallet(
                    WalletType.USER,
                    request.description(),
                    request.ownerId(),
                    OwnerType.USER_OWNER
            );
        }

        // Apply currency if provided
        if (request.currency() != null && !request.currency().isEmpty()) {
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalStateException("Wallet not found after creation: " + walletId));
            wallet.setCurrency(request.currency());
            walletRepository.save(wallet);
        }

        Wallet createdWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        WalletResponse response = toWalletResponse(createdWallet);

        log.info("USER wallet created successfully: walletId={}", walletId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<WalletResponse> createMerchantWallet(CreateMerchantWalletRequest request) {
        log.info("Creating MERCHANT wallet: ownerId={}, currency={}, initialBalance={}",
                request.ownerId(), request.currency(), request.initialBalance());

        Integer walletId;

        if (request.initialBalance() != null && request.initialBalance() > 0) {
            walletId = walletManagementService.createNewWalletWithBalance(
                    WalletType.MERCHANT,
                    request.description(),
                    request.initialBalance(),
                    request.ownerId(),
                    OwnerType.MERCHANT_OWNER
            );
        } else {
            walletId = walletManagementService.createNewWallet(
                    WalletType.MERCHANT,
                    request.description(),
                    request.ownerId(),
                    OwnerType.MERCHANT_OWNER
            );
        }

        // Apply currency if provided
        if (request.currency() != null && !request.currency().isEmpty()) {
            Wallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalStateException("Wallet not found after creation: " + walletId));
            wallet.setCurrency(request.currency());
            walletRepository.save(wallet);
        }

        Wallet createdWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found: " + walletId));

        WalletResponse response = toWalletResponse(createdWallet);

        log.info("MERCHANT wallet created successfully: walletId={}", walletId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<WalletResponse> getWallet(Integer walletId) {
        log.debug("Getting wallet: walletId={}", walletId);

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));

        WalletResponse response = toWalletResponse(wallet);

        return ResponseEntity.ok(response);
    }

    /**
     * Converts Wallet entity to WalletResponse DTO.
     *
     * @param wallet Wallet entity
     * @return WalletResponse DTO
     */
    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getType().name(),
                wallet.getOwnerId(),
                wallet.getOwnerType().name(),
                wallet.getDescription(),
                wallet.getCurrency()
        );
    }
}
