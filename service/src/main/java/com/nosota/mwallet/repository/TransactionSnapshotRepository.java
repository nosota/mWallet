package com.nosota.mwallet.repository;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class TransactionSnapshotRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public TransactionSnapshot save(TransactionSnapshot snapshot) {
        entityManager.persist(snapshot);
        return snapshot;
    }

    public List<TransactionSnapshot> findByWalletId(Long walletId) {
        TypedQuery<TransactionSnapshot> query = entityManager.createQuery(
                "SELECT w FROM TransactionSnapshot w WHERE w.walletId = :walletId", TransactionSnapshot.class);
        query.setParameter("walletId", walletId);
        return query.getResultList();
    }

    public void saveAll(List<TransactionSnapshot> snapshots) {
        for (TransactionSnapshot snapshot : snapshots) {
            entityManager.persist(snapshot);
        }
    }

    public Long getConfirmedBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM TransactionSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.CONFIRMED);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }

    public Long getHoldBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM TransactionSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.HOLD);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }

    public Long getRejectedBalance(Long walletId) {
        String jpql = "SELECT SUM(ws.amount) FROM TransactionSnapshot ws WHERE ws.walletId = :walletId AND ws.status = :status";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("walletId", walletId);
        query.setParameter("status", TransactionStatus.REJECTED);

        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }
}
