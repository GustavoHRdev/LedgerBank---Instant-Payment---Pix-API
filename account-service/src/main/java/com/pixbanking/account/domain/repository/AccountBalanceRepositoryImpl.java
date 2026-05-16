package com.pixbanking.account.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class AccountBalanceRepositoryImpl implements AccountBalanceRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public BigDecimal calculateBalanceByAccountId(UUID accountId) {
        BigDecimal balance = (BigDecimal) entityManager.createNativeQuery("""
                select coalesce(sum(le.amount), 0.0000)
                from ledger_entries le
                where le.account_id = :accountId
                """)
                .setParameter("accountId", accountId)
                .getSingleResult();
        return balance.setScale(4);
    }
}
