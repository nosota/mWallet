package com.nosota.mwallet.api;

import com.nosota.mwallet.api.request.CreateMerchantWalletRequest;
import com.nosota.mwallet.api.request.CreateUserWalletRequest;
import com.nosota.mwallet.api.response.WalletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Wallet API interface for tier2 internal business service.
 *
 * <p>Defines REST endpoints for wallet management operations:
 * <ul>
 *   <li>Creating USER wallets (owned by users)</li>
 *   <li>Creating MERCHANT wallets (owned by merchants)</li>
 *   <li>Retrieving wallet information</li>
 * </ul>
 *
 * <p><b>Security Note:</b> ESCROW and SYSTEM wallets cannot be created via public API.
 * They are created internally by the system as needed.
 *
 * <p>This interface is implemented by:
 * <ul>
 *   <li>WalletController - in service module (server-side implementation)</li>
 *   <li>WalletClient - in api module (WebClient-based client for consumers)</li>
 * </ul>
 */
@RequestMapping("/api/v1/wallets")
public interface WalletApi {

    /**
     * Creates a new USER wallet.
     *
     * <p>USER wallets are owned by individual users and require a non-null ownerId.
     * They can optionally be created with an initial balance using proper double-entry bookkeeping.
     *
     * @param request Wallet creation request containing ownerId, description, currency, and optional initialBalance
     * @return Created wallet response with wallet ID and details
     */
    @PostMapping("/user")
    ResponseEntity<WalletResponse> createUserWallet(@RequestBody @Valid CreateUserWalletRequest request);

    /**
     * Creates a new MERCHANT wallet.
     *
     * <p>MERCHANT wallets are owned by merchants and require a non-null ownerId.
     * They can optionally be created with an initial balance using proper double-entry bookkeeping.
     *
     * @param request Wallet creation request containing ownerId, description, currency, and optional initialBalance
     * @return Created wallet response with wallet ID and details
     */
    @PostMapping("/merchant")
    ResponseEntity<WalletResponse> createMerchantWallet(@RequestBody @Valid CreateMerchantWalletRequest request);

    /**
     * Retrieves wallet information by ID.
     *
     * @param walletId Unique identifier of the wallet
     * @return Wallet information including type, owner, currency, and description
     */
    @GetMapping("/{walletId}")
    ResponseEntity<WalletResponse> getWallet(@PathVariable("walletId") Integer walletId);
}
