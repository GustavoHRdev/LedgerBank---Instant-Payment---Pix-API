package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
}
