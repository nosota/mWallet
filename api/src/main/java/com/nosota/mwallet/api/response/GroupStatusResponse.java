package com.nosota.mwallet.api.response;

import java.util.UUID;

/**
 * Response for transaction group status query.
 */
public record GroupStatusResponse(
        UUID referenceId,
        String status
) {}
