package com.nosota.mwallet.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * Represents a wallet in the system.
 * <p>
 * Wallets have different types (USER, MERCHANT, ESCROW, SYSTEM) with different ownership rules:
 * - USER/MERCHANT wallets must have a non-null ownerId (the user/merchant who owns it)
 * - ESCROW/SYSTEM wallets must have ownerId=null (owned by the system)
 * </p>
 * <p>
 * Database constraints enforce these rules via CHECK constraints in migration V2.04.
 * </p>
 */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Type of the wallet (USER, MERCHANT, ESCROW, SYSTEM).
     * Determines ownership rules and allowed operations.
     */
    @Enumerated(EnumType.STRING)
    private WalletType type;

    /**
     * ID of the owner (user or merchant).
     * <p>
     * - For USER wallets: ID of the user in the user service
     * - For MERCHANT wallets: ID of the merchant
     * - For ESCROW/SYSTEM wallets: must be null (system-owned)
     * </p>
     * <p>
     * Database constraints:
     * - USER/MERCHANT wallets: ownerId must be NOT NULL
     * - ESCROW/SYSTEM wallets: ownerId must be NULL
     * </p>
     */
    private Long ownerId;

    /**
     * Type of the owner (USER_OWNER, MERCHANT_OWNER, SYSTEM_OWNER).
     * <p>
     * - USER_OWNER: wallet belongs to a user (type=USER)
     * - MERCHANT_OWNER: wallet belongs to a merchant (type=MERCHANT)
     * - SYSTEM_OWNER: wallet is system-owned (type=ESCROW or SYSTEM)
     * </p>
     * <p>
     * Database constraints enforce consistency between type and ownerType.
     * </p>
     */
    @Enumerated(EnumType.STRING)
    private OwnerType ownerType;

    /**
     * Optional description of the wallet.
     */
    private String description;

    /**
     * Currency of the wallet (ISO 4217 code: USD, EUR, RUB, etc.).
     * <p>
     * All transactions on this wallet must use the same currency.
     * Transfers between wallets with different currencies are forbidden.
     * </p>
     * <p>
     * Default: USD
     * </p>
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
}
