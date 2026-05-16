package com.pixbanking.payment.domain.repository;

import com.pixbanking.payment.domain.model.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
}
