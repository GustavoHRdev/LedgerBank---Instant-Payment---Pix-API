package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {

    boolean existsByMessageId(String messageId);
}
