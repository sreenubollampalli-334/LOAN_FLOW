package com.loanflow.notification.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.notification.dto.MarkAllReadResponse;
import com.loanflow.notification.dto.NotificationResponse;
import com.loanflow.notification.dto.UnreadCountResponse;
import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import com.loanflow.notification.repository.NotificationRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<NotificationResponse> getMyNotifications(NotificationType type, NotificationStatus status, int page, int size) {
        User user = getAuthenticatedUser();
        
        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Page<Notification> notificationPage;

        if (type != null && status != null) {
            notificationPage = notificationRepository.findByRecipientIdAndTypeAndStatus(user.getId(), type, status, pageable);
        } else if (type != null) {
            notificationPage = notificationRepository.findByRecipientIdAndType(user.getId(), type, pageable);
        } else if (status != null) {
            notificationPage = notificationRepository.findByRecipientIdAndStatus(user.getId(), status, pageable);
        } else {
            notificationPage = notificationRepository.findByRecipientId(user.getId(), pageable);
        }

        List<NotificationResponse> content = notificationPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                content,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages(),
                notificationPage.isFirst(),
                notificationPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public NotificationResponse getMyNotificationById(Long id) {
        User user = getAuthenticatedUser();

        Notification notification = notificationRepository.findByIdAndRecipientId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        return mapToResponse(notification);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount() {
        User user = getAuthenticatedUser();
        long count = notificationRepository.countByRecipientIdAndStatus(user.getId(), NotificationStatus.UNREAD);
        return new UnreadCountResponse(count);
    }

    @Transactional
    public NotificationResponse markAsRead(Long id) {
        User user = getAuthenticatedUser();

        Notification notification = notificationRepository.findByIdAndRecipientId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (NotificationStatus.UNREAD.equals(notification.getStatus())) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return mapToResponse(notification);
    }

    @Transactional
    public MarkAllReadResponse markAllAsRead() {
        User user = getAuthenticatedUser();
        int updatedCount = notificationRepository.bulkUpdateStatusByRecipientId(user.getId(), NotificationStatus.UNREAD, NotificationStatus.READ);
        return new MarkAllReadResponse(updatedCount);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setType(notification.getType());
        response.setSubject(notification.getSubject());
        response.setContent(notification.getContent());
        response.setStatus(notification.getStatus());
        response.setResourceType(notification.getResourceType());
        response.setResourceId(notification.getResourceId());
        response.setMetadata(notification.getMetadata());
        response.setReadAt(notification.getReadAt());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
