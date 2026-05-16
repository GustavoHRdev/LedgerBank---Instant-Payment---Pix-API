package com.pixbanking.payment.domain.repository;

import com.pixbanking.payment.domain.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {

    @Query(value = """
            select coalesce(sum(case
                when le.entry_type in ('CREDIT', 'REVERSAL_CREDIT') then le.amount
                when le.entry_type in ('DEBIT', 'REVERSAL_DEBIT') then -le.amount
            end), 0.0000)
            from ledger_entries le
            where le.account_id = :accountId
            """, nativeQuery = true)
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);

    @Query(value = """
            select le.running_balance
            from ledger_entries le
            where le.account_id = :accountId
            order by le.created_at desc
            limit 1
            """, nativeQuery = true)
    Optional<BigDecimal> findLastRunningBalance(@Param("accountId") UUID accountId);
}
