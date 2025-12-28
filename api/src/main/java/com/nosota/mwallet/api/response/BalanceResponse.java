package com.nosota.mwallet.api.response;

/**
 * Response for wallet balance query.
 */
public record BalanceResponse(
        Integer walletId,
        Long availableBalance
) {}
