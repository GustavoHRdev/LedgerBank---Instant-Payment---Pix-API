package com.pixbanking.account.service;

import com.pixbanking.account.domain.model.Account;
import com.pixbanking.account.domain.repository.AccountRepository;
import com.pixbanking.account.dto.AccountBalanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    public AccountQueryService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountBalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        return new AccountBalanceResponse(
                accountId,
                accountRepository.calculateBalanceByAccountId(accountId),
                account.getCurrency()
        );
    }
}
