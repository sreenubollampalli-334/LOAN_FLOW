package com.loanflow.notification.scheduler;

import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.notification.service.OutboxNotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class OutboxNotificationScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxNotificationService outboxNotificationService;

    public OutboxNotificationScheduler(OutboxEventRepository outboxEventRepository,
                                       OutboxNotificationService outboxNotificationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxNotificationService = outboxNotificationService;
    }

    @Scheduled(fixedDelayString = "${loanflow.outbox.notification.delay:5000}")
    public void processPendingNotifications() {
        Pageable limit = PageRequest.of(0, 50);
        
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING", limit);
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("FAILED", limit);
        
        List<OutboxEvent> allEvents = new ArrayList<>();
        allEvents.addAll(pendingEvents);
        allEvents.addAll(failedEvents);

        for (OutboxEvent event : allEvents) {
            if (event.getNextAttemptAt() != null && event.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                continue;
            }

            try {
                // Try to acquire the lock to process it (optimistic handling)
                // If it fails, updateStatusAndProcessedAt will return 0, but we moved to a simpler design
                // which relies on standard database row locks if concurrency is low, or we can use the 
                // explicitly added updateStatusAndProcessedAt method to lock it.
                // Let's use the updateStatusAndProcessedAt method to atomically move it to PROCESSING state 
                // before passing to the service to ensure exact once idempotency across concurrent pods!
                int updated = outboxEventRepository.updateStatusAndProcessedAt(
                        event.getId(), event.getStatus(), "PROCESSING", LocalDateTime.now());
                
                if (updated > 0) {
                    outboxNotificationService.processEvent(event.getId());
                }
            } catch (Exception e) {
                outboxNotificationService.handleFailure(event.getId(), e.getMessage());
            }
        }
    }
}
