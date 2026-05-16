package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByAccountIdAndIdempotencyKey(UUID accountId, String idempotencyKey);
}
