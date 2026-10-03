package com.loanflow.outbox.repository;

import com.loanflow.outbox.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByStatus(String status);
    
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.processedAt = :processedAt WHERE e.id = :id AND e.status = :oldStatus")
    int updateStatusAndProcessedAt(@Param("id") Long id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus, @Param("processedAt") LocalDateTime processedAt);
    
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :newStatus, e.retryCount = e.retryCount + 1, e.failureDetails = :failureDetails, e.nextAttemptAt = :nextAttemptAt WHERE e.id = :id AND e.status = :oldStatus")
    int markFailed(@Param("id") Long id, @Param("oldStatus") String oldStatus, @Param("newStatus") String newStatus, @Param("failureDetails") String failureDetails, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
