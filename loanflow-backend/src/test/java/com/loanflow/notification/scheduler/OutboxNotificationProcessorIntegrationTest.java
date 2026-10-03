package com.loanflow.notification.scheduler;

import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.notification.entity.Notification;
import com.loanflow.notification.entity.NotificationStatus;
import com.loanflow.notification.repository.NotificationRepository;
import com.loanflow.notification.service.OutboxNotificationService;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
        "spring.datasource.username=loanflow_user",
        "spring.datasource.password=loanflow_pass",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "loanflow.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "loanflow.outbox.notification.delay=9999999" // disable auto-running
})
public class OutboxNotificationProcessorIntegrationTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CustomerProfileRepository customerProfileRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private LoanRepository loanRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private OutboxNotificationScheduler outboxNotificationScheduler;
    @Autowired
    private OutboxNotificationService outboxNotificationService;

    private User testUser;
    private LoanApplication testApp;
    private Loan testLoan;

    @BeforeEach
    public void setUp() {
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
        
        // Setup basic data
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        String uniqueSuffix = System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 5);

        testUser = new User();
        testUser.setEmail("outboxuser-" + uniqueSuffix + "@example.com");
        testUser.setPasswordHash("hash");
        testUser.setIsActive(true);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(testUser);
        profile.setFirstName("Outbox");
        profile.setLastName("Test");
        profile.setDob(LocalDate.of(1990, 1, 1));
        profile.setPhone("1234" + uniqueSuffix.substring(0, 6)); // ensure unique phone if constrained
        profile = customerProfileRepository.save(profile);

        LoanProduct product = new LoanProduct();
        product.setName("Test Product " + uniqueSuffix);
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("5000"));
        product.setInterestRate(new BigDecimal("0.05"));
        product.setMinTermMonths(6);
        product.setMaxTermMonths(12);
        product.setIsActive(true);
        product = loanProductRepository.save(product);

        testApp = new LoanApplication();
        testApp.setCustomer(profile);
        testApp.setProduct(product);
        testApp.setRequestedAmount(new BigDecimal("2000"));
        testApp.setTermMonths(12);
        testApp.setStatus("SUBMITTED");
        testApp = loanApplicationRepository.save(testApp);

        testLoan = new Loan();
        testLoan.setCustomer(profile);
        testLoan.setProduct(product);
        testLoan.setApplication(testApp);
        testLoan.setPrincipalAmount(new BigDecimal("2000"));
        testLoan.setInterestRate(new BigDecimal("0.05"));
        testLoan.setTermMonths(12);
        testLoan.setStatus("DISBURSED");
        testLoan.setInterestCalculationMethod("FLAT");
        testLoan = loanRepository.save(testLoan);
    }

    @Test
    public void testSuccessfulProcessing() {
        // A. Successful processing
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("LoanApplication");
        event.setAggregateId(testApp.getId());
        event.setType("MANAGER_REVIEW_SUBMITTED");
        event.setStatus("PENDING");
        event.setPayload("{\"managerReviewId\": 100, \"decision\": \"APPROVE\"}");
        outboxEventRepository.save(event);

        outboxNotificationScheduler.processPendingNotifications();

        // Check Outbox state
        OutboxEvent processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo("PROCESSED");
        assertThat(processed.getProcessedAt()).isNotNull();

        // Check Notification state
        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification notif = notifications.get(0);
        assertThat(notif.getRecipient().getId()).isEqualTo(testUser.getId());
        assertThat(notif.getType().name()).isEqualTo("MANAGER_REVIEW");
        assertThat(notif.getSubject()).isEqualTo("Manager Review Completed");
        assertThat(notif.getContent()).contains("APPROVE");
        assertThat(notif.getResourceType()).isEqualTo("MANAGER_REVIEW");
        assertThat(notif.getResourceId()).isEqualTo(100L);
        assertThat(notif.getStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    public void testIdempotency() {
        // B. Idempotency
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Loan");
        event.setAggregateId(testLoan.getId());
        event.setType("LOAN_DISBURSED");
        event.setStatus("PENDING");
        event.setPayload("{\"disbursementId\": 200, \"amount\": \"2000.00\"}");
        outboxEventRepository.save(event);

        // Run processor first time
        outboxNotificationScheduler.processPendingNotifications();
        assertThat(notificationRepository.findAll()).hasSize(1);

        // Run processor second time - should not duplicate because event is already PROCESSED
        outboxNotificationScheduler.processPendingNotifications();
        outboxNotificationScheduler.processPendingNotifications();

        assertThat(notificationRepository.findAll()).hasSize(1); // Still 1 because event is PROCESSED!
    }

    @Test
    public void testMultipleEvents() {
        // C. Multiple events
        OutboxEvent event1 = new OutboxEvent();
        event1.setAggregateType("LoanApplication");
        event1.setAggregateId(testApp.getId());
        event1.setType("CREDIT_REVIEW_SUBMITTED");
        event1.setStatus("PENDING");
        event1.setPayload("{\"reviewId\": 10, \"decision\": \"APPROVE\"}");
        
        OutboxEvent event2 = new OutboxEvent();
        event2.setAggregateType("LoanApplication");
        event2.setAggregateId(testApp.getId());
        event2.setType("MANAGER_REVIEW_SUBMITTED");
        event2.setStatus("PENDING");
        event2.setPayload("{\"managerReviewId\": 20, \"decision\": \"REJECT\"}");
        
        outboxEventRepository.saveAll(List.of(event1, event2));

        outboxNotificationScheduler.processPendingNotifications();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(2);
        assertThat(outboxEventRepository.findByStatus("PROCESSED")).hasSize(2); // The initial event from setup is now PROCESSED too, wait no, outbox was deleted before this test.
    }

    @Test
    public void testFailure() {
        // D. Failure
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("LoanApplication");
        event.setAggregateId(testApp.getId());
        event.setType("MANAGER_REVIEW_SUBMITTED");
        event.setStatus("PROCESSING");
        event.setPayload("{}"); 
        outboxEventRepository.save(event);

        // Directly call handleFailure to simulate caught exception
        outboxNotificationService.handleFailure(event.getId(), "Simulated failure");

        // Check Outbox state - should be FAILED
        OutboxEvent failed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getFailureDetails()).contains("Simulated failure");
        assertThat(failed.getNextAttemptAt()).isNotNull();

        // Ensure no notification created
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    public void testUnsupportedEvents() {
        // E. Unsupported events
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("LoanApplication");
        event.setAggregateId(testApp.getId());
        event.setType("SOME_UNKNOWN_EVENT_TYPE");
        event.setStatus("PENDING");
        event.setPayload("{}"); 
        outboxEventRepository.save(event);

        outboxNotificationScheduler.processPendingNotifications();

        // Should be marked PROCESSED but no notification created
        OutboxEvent processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo("PROCESSED");
        assertThat(notificationRepository.findAll()).isEmpty();
    }
}
