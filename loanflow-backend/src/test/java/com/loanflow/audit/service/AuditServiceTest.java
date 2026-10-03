package com.loanflow.audit.service;

import com.loanflow.audit.entity.AuditLog;
import com.loanflow.audit.repository.AuditLogRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditService auditService;

    private User mockUser;

    @BeforeEach
    public void setUp() {
        mockUser = new User();
        mockUser.setId(10L);
        mockUser.setEmail("test@example.com");
    }

    @Test
    public void testRecordAudit_SuccessWithActor() {
        // Mock Security Context
        UserDetailsImpl userDetails = new UserDetailsImpl(mockUser);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(10L)).thenReturn(Optional.of(mockUser));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        AuditLog saved = auditService.recordAudit("TEST_ACTION", "TestEntity", 1L, null, "{\"data\":\"test\"}", "127.0.0.1");

        assertNotNull(saved);
        assertEquals("TEST_ACTION", saved.getAction());
        assertEquals("TestEntity", saved.getEntityType());
        assertEquals(1L, saved.getEntityId());
        assertEquals(mockUser, saved.getActor());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("{\"data\":\"test\"}", saved.getNewValue());
    }

    @Test
    public void testRecordAudit_NoAuthenticatedUser() {
        SecurityContextHolder.clearContext();
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        AuditLog saved = auditService.recordAudit("TEST_ACTION", "TestEntity", 1L, null, "{\"data\":\"test\"}", "127.0.0.1");

        assertNotNull(saved);
        assertNull(saved.getActor()); // Actor should be null if no one is authenticated
    }

    @Test
    public void testRecordAudit_SensitiveDataSanitized() {
        SecurityContextHolder.clearContext(); // No user needed for this test
        
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        String sensitiveJson = "{\"password\": \"secret123\", \"username\":\"user\", \"token\":\"jwt_abc\", \"document_key\":\"aws_s3_key\"}";
        AuditLog saved = auditService.recordAudit("TEST_ACTION", "TestEntity", 1L, null, sensitiveJson, "127.0.0.1");

        String newValue = saved.getNewValue();
        assertNotNull(newValue);
        assertTrue(newValue.contains("\"password\": \"***\""));
        assertTrue(newValue.contains("\"token\":\"***\""));
        assertTrue(newValue.contains("\"document_key\":\"***\""));
        assertTrue(newValue.contains("\"username\":\"user\"")); // username is not sensitive
    }
}
