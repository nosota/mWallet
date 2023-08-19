package com.nosota.mwallet.repository;
import com.nosota.mwallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Custom query methods (if needed) can be defined here

    // Example: Find wallet by a custom attribute like 'name'
    Optional<Wallet> findById(Integer id);
}
