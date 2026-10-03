package com.loanflow.audit.service;

import com.loanflow.audit.entity.AuditLog;
import com.loanflow.audit.repository.AuditLogRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditLog recordAudit(String action, String entityType, Long entityId, String oldValue, String newValue, String ipAddress) {
        User actor = getAuthenticatedUser();

        AuditLog auditLog = new AuditLog();
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setOldValue(sanitize(oldValue));
        auditLog.setNewValue(sanitize(newValue));
        auditLog.setIpAddress(ipAddress);
        
        return auditLogRepository.save(auditLog);
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) auth.getPrincipal()).getUser().getId();
            return userRepository.findById(userId).orElse(null);
        }
        return null;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        value = value.replaceAll("(?i)(\"password\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"token\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"jwt\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"secret\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"bank_account_number\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"document_key\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        value = value.replaceAll("(?i)(\"storage_key\"\\s*:\\s*\")[^\"]+(\")", "$1***$2");
        return value;
    }
}
