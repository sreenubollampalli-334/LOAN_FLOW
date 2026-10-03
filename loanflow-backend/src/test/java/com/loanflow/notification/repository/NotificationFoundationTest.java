package com.loanflow.notification.repository;

import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.entity.NotificationType;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
    "spring.datasource.username=loanflow_user",
    "spring.datasource.password=loanflow_pass",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "loanflow.security.jwt.secret=testsecret123456789012345678901234",
    "loanflow.storage.s3.endpoint=http://localhost:9000",
    "loanflow.storage.s3.region=us-east-1",
    "loanflow.storage.s3.bucket=test",
    "loanflow.storage.s3.access-key=test",
    "loanflow.storage.s3.secret-key=test"
})
@Transactional
public class NotificationFoundationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testNotificationPersistenceAndDefaultState() {
        // Create or get a user
        User user = new User();
        user.setEmail("testnotif@example.com");
        user.setPasswordHash("hash");
        user = userRepository.save(user);

        Notification notif = new Notification();
        notif.setRecipient(user);
        notif.setType(NotificationType.APPLICATION);
        notif.setSubject("Test Subject");
        notif.setContent("Test Content");
        notif.setResourceType("APPLICATION");
        notif.setResourceId(123L);
        notif.setMetadata("{\"key\":\"value\"}");

        // Save
        Notification saved = notificationRepository.save(notif);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(NotificationStatus.UNREAD, saved.getStatus(), "Default status should be UNREAD");
        assertEquals(NotificationType.APPLICATION, saved.getType());
        assertEquals("APPLICATION", saved.getResourceType());
        assertEquals(123L, saved.getResourceId());
        assertEquals("{\"key\":\"value\"}", saved.getMetadata());

        // Test custom query
        List<Notification> unreadNotifs = notificationRepository.findByRecipientIdAndStatus(user.getId(), NotificationStatus.UNREAD);
        assertFalse(unreadNotifs.isEmpty());
        assertEquals(saved.getId(), unreadNotifs.get(0).getId());
    }
}
