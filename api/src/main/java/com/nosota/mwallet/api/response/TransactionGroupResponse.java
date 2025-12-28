package com.nosota.mwallet.api.response;

import java.util.UUID;

/**
 * Response for transaction group operations (create, settle, release, cancel).
 */
public record TransactionGroupResponse(
        UUID referenceId,
        String status,
        String message
) {}
