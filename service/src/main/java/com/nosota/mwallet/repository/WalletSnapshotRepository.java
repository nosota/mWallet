package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.WalletSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class WalletSnapshotRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public WalletSnapshot save(WalletSnapshot snapshot) {
        entityManager.persist(snapshot);
        return snapshot;
    }

    public List<WalletSnapshot> findByWalletId(Long walletId) {
        TypedQuery<WalletSnapshot> query = entityManager.createQuery(
                "SELECT w FROM WalletSnapshot w WHERE w.walletId = :walletId", WalletSnapshot.class);
        query.setParameter("walletId", walletId);
        return query.getResultList();
    }

    public void saveAll(List<WalletSnapshot> snapshots) {
        for (WalletSnapshot snapshot : snapshots) {
            entityManager.persist(snapshot);
        }
    }

    public Long getConfirmedBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM WalletSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.CONFIRMED);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }

    public Long getHoldBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM WalletSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.HOLD);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }

    public Long getRejectedBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM WalletSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.REJECTED);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }
}
