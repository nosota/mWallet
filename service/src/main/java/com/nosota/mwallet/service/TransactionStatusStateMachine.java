package com.nosota.mwallet.service;

import com.nosota.mwallet.api.model.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * State machine for validating TransactionStatus transitions.
 *
 * <p>Implements strict validation rules for transaction lifecycle:
 * <ul>
 *   <li>HOLD is the initial state (created by holdDebit/holdCredit)</li>
 *   <li>HOLD can transition to: SETTLED, RELEASED, CANCELLED</li>
 *   <li>All final states (SETTLED, RELEASED, CANCELLED, REFUNDED) are immutable</li>
 * </ul>
 *
 * <p>State diagram:
 * <pre>
 *           HOLD
 *            |
 *     +------+------+
 *     |      |      |
 * SETTLED RELEASED CANCELLED
 *
 * REFUNDED (special case: direct creation for refund operations)
 * </pre>
 */
@Component
public class TransactionStatusStateMachine {

    /**
     * Map of allowed transitions: fromStatus → Set of valid toStatus values.
     */
    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = Map.of(
            // HOLD can transition to any final state
            TransactionStatus.HOLD, EnumSet.of(
                    TransactionStatus.SETTLED,
                    TransactionStatus.RELEASED,
                    TransactionStatus.CANCELLED
            )
            // SETTLED, RELEASED, CANCELLED, REFUNDED are final states - no transitions allowed
            // Note: REFUNDED is created directly for refund operations, not via state transition
    );

    /**
     * Validates if a status transition is allowed.
     *
     * @param fromStatus Current status
     * @param toStatus   Target status
     * @return true if transition is allowed, false otherwise
     */
    public boolean isTransitionAllowed(TransactionStatus fromStatus, TransactionStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }

        // Same status is always allowed (no-op)
        if (fromStatus == toStatus) {
            return true;
        }

        // Check if transition is in allowed map
        Set<TransactionStatus> allowedTargets = ALLOWED_TRANSITIONS.get(fromStatus);
        return allowedTargets != null && allowedTargets.contains(toStatus);
    }

    /**
     * Validates if a status transition is allowed, throwing exception if not.
     *
     * @param fromStatus Current status
     * @param toStatus   Target status
     * @throws IllegalStateException if transition is not allowed
     */
    public void validateTransition(TransactionStatus fromStatus, TransactionStatus toStatus) {
        if (!isTransitionAllowed(fromStatus, toStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid transaction status transition: %s → %s. " +
                                    "Allowed transitions from %s: %s",
                            fromStatus, toStatus, fromStatus,
                            ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()))
            );
        }
    }

    /**
     * Checks if a status is a final state (no further transitions allowed).
     *
     * @param status Status to check
     * @return true if status is final (immutable)
     */
    public boolean isFinalState(TransactionStatus status) {
        return status == TransactionStatus.SETTLED
                || status == TransactionStatus.RELEASED
                || status == TransactionStatus.CANCELLED
                || status == TransactionStatus.REFUNDED;
    }

    /**
     * Gets all allowed target statuses from a given status.
     *
     * @param fromStatus Current status
     * @return Set of allowed target statuses (empty if none allowed)
     */
    public Set<TransactionStatus> getAllowedTransitions(TransactionStatus fromStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of());
    }
}
