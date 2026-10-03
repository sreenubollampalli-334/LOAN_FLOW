package com.loanflow.notification.repository;

import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientId(Long recipientId);
    List<Notification> findByRecipientIdAndStatus(Long recipientId, NotificationStatus status);

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);
    Page<Notification> findByRecipientIdAndType(Long recipientId, NotificationType type, Pageable pageable);
    Page<Notification> findByRecipientIdAndStatus(Long recipientId, NotificationStatus status, Pageable pageable);
    Page<Notification> findByRecipientIdAndTypeAndStatus(Long recipientId, NotificationType type, NotificationStatus status, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    long countByRecipientIdAndStatus(Long recipientId, NotificationStatus status);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :newStatus, n.readAt = CURRENT_TIMESTAMP WHERE n.recipient.id = :recipientId AND n.status = :oldStatus")
    int bulkUpdateStatusByRecipientId(@Param("recipientId") Long recipientId, @Param("oldStatus") NotificationStatus oldStatus, @Param("newStatus") NotificationStatus newStatus);
}
