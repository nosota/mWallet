package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.model.WalletType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Integer> {
    /**
     * Retrieves the {@link Wallet} entity associated with the specified wallet ID and locks it for update.
     * <p>
     * This method utilizes the <b>pessimistic write lock</b> strategy, ensuring exclusive access to
     * the {@link Wallet} entity in scenarios requiring data consistency and preventing concurrent modifications.
     * It's mainly used in situations where the risk of concurrent updates could lead to data integrity issues.
     * </p>
     * <p>
     * <b>Note:</b> Pessimistic write locks can lead to database-level locks, potentially causing other transactions
     * to be blocked until the lock is released. Ensure the surrounding transaction is as short as possible to minimize
     * the duration of the lock.
     * </p>
     * <p>
     * <b>Warning:</b> Overusing pessimistic locks can adversely affect system throughput and scalability. It's recommended
     * to use this method judiciously and only when necessary.
     * </p>
     *
     * @param id The unique identifier (ID) of the wallet to be retrieved and locked.
     * @return The {@link Wallet} entity corresponding to the provided ID, locked for update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Wallet getOneForUpdate(@Param("id") Integer id);

    /**
     * Finds wallets by type and null ownerId (system wallets).
     * Used for finding ESCROW and SYSTEM wallets.
     *
     * @param type The wallet type (ESCROW, SYSTEM)
     * @return List of wallets (typically 0 or 1)
     */
    List<Wallet> findByTypeAndOwnerIdIsNull(WalletType type);

    /**
     * Finds wallets by ownerId and type.
     * Used for finding USER and MERCHANT wallets.
     *
     * @param ownerId The owner ID
     * @param type    The wallet type (USER, MERCHANT)
     * @return List of wallets
     */
    List<Wallet> findByOwnerIdAndType(Long ownerId, WalletType type);

    /**
     * Finds a wallet by type and description.
     * Used for finding system wallets like DEPOSIT, WITHDRAWAL.
     *
     * @param type        The wallet type (typically SYSTEM)
     * @param description The wallet description
     * @return Optional containing the wallet if found
     */
    Optional<Wallet> findByTypeAndDescription(WalletType type, String description);
}
