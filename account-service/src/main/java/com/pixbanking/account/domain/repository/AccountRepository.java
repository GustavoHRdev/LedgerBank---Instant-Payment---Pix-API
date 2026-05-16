package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, AccountBalanceRepository {
}
