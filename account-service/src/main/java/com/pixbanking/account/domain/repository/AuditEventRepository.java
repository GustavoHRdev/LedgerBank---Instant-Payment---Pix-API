package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
