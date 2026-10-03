package com.loanflow.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import com.loanflow.notification.repository.NotificationRepository;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OutboxNotificationService {

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final ObjectMapper objectMapper;

    public OutboxNotificationService(OutboxEventRepository outboxEventRepository,
                                     NotificationRepository notificationRepository,
                                     LoanApplicationRepository loanApplicationRepository,
                                     LoanRepository loanRepository,
                                     ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.notificationRepository = notificationRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanRepository = loanRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));

        if (!"PROCESSING".equals(event.getStatus())) {
            return; // Already processed or not in PROCESSING state
        }

        User recipient = resolveRecipient(event);
        if (recipient != null) {
            Notification notification = buildNotification(event, recipient);
            if (notification != null) {
                // Store event ID in metadata for traceability and idempotency
                try {
                    String meta = "{\"outboxEventId\":" + event.getId() + "}";
                    notification.setMetadata(meta);
                } catch (Exception ignored) {}

                notificationRepository.save(notification);
            }
        }

        event.setStatus("PROCESSED");
        event.setProcessedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long eventId, String error) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            if (!"PROCESSED".equals(event.getStatus())) {
                event.setStatus("FAILED");
                event.setFailureDetails(error != null ? error.substring(0, Math.min(error.length(), 250)) : "Unknown error");
                event.setRetryCount(event.getRetryCount() + 1);
                
                // Exponential backoff
                long backoffSeconds = (long) Math.pow(2, event.getRetryCount()) * 10;
                event.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                
                outboxEventRepository.save(event);
            }
        });
    }

    private User resolveRecipient(OutboxEvent event) {
        if ("LoanApplication".equals(event.getAggregateType())) {
            Optional<LoanApplication> appOpt = loanApplicationRepository.findById(event.getAggregateId());
            return appOpt.map(app -> app.getCustomer().getUser()).orElse(null);
        } else if ("Loan".equals(event.getAggregateType())) {
            Optional<Loan> loanOpt = loanRepository.findById(event.getAggregateId());
            return loanOpt.map(loan -> loan.getCustomer().getUser()).orElse(null);
        }
        return null;
    }

    private Notification buildNotification(OutboxEvent event, User recipient) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            Notification notif = new Notification();
            notif.setRecipient(recipient);
            notif.setStatus(NotificationStatus.UNREAD);

            switch (event.getType()) {
                case "MANAGER_REVIEW_SUBMITTED":
                    notif.setType(NotificationType.MANAGER_REVIEW);
                    notif.setSubject("Manager Review Completed");
                    notif.setContent("Your application has been reviewed by a manager. Decision: " + payload.path("decision").asText());
                    notif.setResourceType("MANAGER_REVIEW");
                    notif.setResourceId(payload.path("managerReviewId").asLong(0));
                    return notif;
                case "CREDIT_REVIEW_SUBMITTED":
                    notif.setType(NotificationType.CREDIT_REVIEW);
                    notif.setSubject("Credit Review Completed");
                    notif.setContent("Your application has been reviewed by a credit officer. Decision: " + payload.path("decision").asText());
                    notif.setResourceType("CREDIT_REVIEW");
                    notif.setResourceId(payload.path("reviewId").asLong(0));
                    return notif;
                case "LOAN_DISBURSED":
                    notif.setType(NotificationType.DISBURSEMENT);
                    notif.setSubject("Loan Disbursed");
                    notif.setContent("Your loan has been successfully disbursed. Amount: " + payload.path("amount").asText());
                    notif.setResourceType("DISBURSEMENT");
                    notif.setResourceId(payload.path("disbursementId").asLong(0));
                    return notif;
                case "DISBURSEMENT_FAILED":
                    notif.setType(NotificationType.DISBURSEMENT);
                    notif.setSubject("Disbursement Failed");
                    notif.setContent("Your loan disbursement failed. Reason: " + payload.path("failureReason").asText());
                    notif.setResourceType("DISBURSEMENT");
                    notif.setResourceId(payload.path("disbursementId").asLong(0));
                    return notif;
                default:
                    // Unsupported event, we ignore and do not create a notification,
                    // but we will mark it PROCESSED so it doesn't get stuck.
                    return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build notification from payload: " + e.getMessage(), e);
        }
    }
}
