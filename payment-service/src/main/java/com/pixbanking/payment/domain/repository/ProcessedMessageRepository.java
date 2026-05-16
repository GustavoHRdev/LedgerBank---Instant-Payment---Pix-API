package com.pixbanking.payment.domain.repository;

import com.pixbanking.payment.domain.model.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {

    boolean existsByMessageId(String messageId);
}
