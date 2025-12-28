package com.nosota.mwallet.api.response;

import java.util.UUID;

/**
 * Response for individual transaction operations (hold, settle, release, cancel).
 */
public record TransactionResponse(
        Integer transactionId,
        Integer walletId,
        Long amount,
        String type,
        String status,
        UUID referenceId
) {}
