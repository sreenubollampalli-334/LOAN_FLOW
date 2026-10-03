package com.loanflow.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import com.loanflow.notification.repository.NotificationRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
        "spring.datasource.username=loanflow_user",
        "spring.datasource.password=loanflow_pass",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "loanflow.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "loanflow.storage.s3.endpoint=http://localhost:9000",
        "loanflow.storage.s3.region=us-east-1",
        "loanflow.storage.s3.bucket=test",
        "loanflow.storage.s3.access-key=test",
        "loanflow.storage.s3.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
public class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private User testUser;
    private UserDetailsImpl userDetails;
    private Notification notif1;
    private Notification notif2;
    private Notification notif3;

    @BeforeEach
    public void setUp() {
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setEmail("notifowner@example.com");
        testUser.setPasswordHash("hash");
        testUser.setIsActive(true);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
        userDetails = new UserDetailsImpl(testUser);

        notif1 = new Notification();
        notif1.setRecipient(testUser);
        notif1.setType(NotificationType.APPLICATION);
        notif1.setSubject("Subject 1");
        notif1.setContent("Content 1");
        notif1.setResourceType("APPLICATION");
        notif1.setResourceId(1L);
        notif1.setStatus(NotificationStatus.UNREAD);
        notif1 = notificationRepository.save(notif1);

        notif2 = new Notification();
        notif2.setRecipient(testUser);
        notif2.setType(NotificationType.PAYMENT);
        notif2.setSubject("Subject 2");
        notif2.setContent("Content 2");
        notif2.setResourceType("PAYMENT");
        notif2.setResourceId(2L);
        notif2.setStatus(NotificationStatus.READ);
        notif2 = notificationRepository.save(notif2);
        
        notif3 = new Notification();
        notif3.setRecipient(testUser);
        notif3.setType(NotificationType.APPLICATION);
        notif3.setSubject("Subject 3");
        notif3.setContent("Content 3");
        notif3.setStatus(NotificationStatus.UNREAD);
        notif3 = notificationRepository.save(notif3);
    }

    @Test
    public void testGetMyNotifications_NoFilters() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    public void testGetMyNotifications_WithTypeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/notifications?type=APPLICATION")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    public void testGetMyNotifications_WithStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/notifications?status=UNREAD")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    public void testGetMyNotificationById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/" + notif1.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notif1.getId()))
                .andExpect(jsonPath("$.subject").value("Subject 1"));
    }

    @Test
    public void testGetMyNotificationById_WrongOwnerReturnsNotFound() throws Exception {
        User otherUser = new User();
        otherUser.setEmail("othernotifuser@example.com");
        otherUser.setPasswordHash("hash");
        otherUser.setIsActive(true);
        otherUser = userRepository.save(otherUser);
        UserDetailsImpl otherUserDetails = new UserDetailsImpl(otherUser);

        mockMvc.perform(get("/api/v1/notifications/" + notif1.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(otherUserDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    public void testMarkAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/" + notif1.getId() + "/read")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"))
                .andExpect(jsonPath("$.readAt").exists());

        // Repeated request is idempotent
        mockMvc.perform(patch("/api/v1/notifications/" + notif1.getId() + "/read")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    public void testMarkAllAsRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    public void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
