package com.pixbanking.account.domain.repository;

import com.pixbanking.account.domain.model.OutboxEvent;
import com.pixbanking.account.domain.model.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
            select oe
            from OutboxEvent oe
            where oe.status = :status
            order by oe.createdAt asc
            """)
    List<OutboxEvent> findTopByStatusOrderByCreatedAtAsc(@Param("status") OutboxEventStatus status, org.springframework.data.domain.Pageable pageable);
}
