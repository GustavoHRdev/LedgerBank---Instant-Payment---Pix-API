package com.pixbanking.account.domain.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountBalanceRepository {

    BigDecimal calculateBalanceByAccountId(UUID accountId);
}
