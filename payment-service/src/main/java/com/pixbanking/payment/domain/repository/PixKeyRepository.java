package com.pixbanking.payment.domain.repository;

import com.pixbanking.payment.domain.model.PixKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PixKeyRepository extends JpaRepository<PixKey, UUID> {

    Optional<PixKey> findByValueAndActiveTrue(String value);
}
