package com.nosota.mwallet.api.response;

import java.util.UUID;

/**
 * Response for transfer operation.
 */
public record TransferResponse(
        UUID referenceId,
        Integer senderId,
        Integer recipientId,
        Long amount,
        String status
) {}
