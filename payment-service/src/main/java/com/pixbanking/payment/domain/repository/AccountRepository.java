package com.pixbanking.payment.domain.repository;

import com.pixbanking.payment.domain.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, AccountLockRepository {
}
