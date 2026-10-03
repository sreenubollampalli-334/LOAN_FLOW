package com.loanflow.loan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import org.mockito.Spy;
import com.loanflow.loan.dto.CreateLoanApplicationRequest;
import com.loanflow.loan.dto.LoanApplicationResponse;
import com.loanflow.loan.dto.LoanApplicationSummaryResponse;
import com.loanflow.loan.entity.ApplicationStatusHistory;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.ApplicationStatusHistoryRepository;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoanApplicationServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private ApplicationStatusHistoryRepository statusHistoryRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private LoanProductRepository loanProductRepository;
    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private LoanApplicationService loanApplicationService;

    private User testUser;
    private CustomerProfile testProfile;
    private LoanProduct testProduct;
    private UserDetailsImpl userDetails;

    @BeforeEach
    public void setUp() {
        testUser = new User();
        testUser.setId(1L);

        testProfile = new CustomerProfile();
        testProfile.setId(10L);
        testProfile.setUser(testUser);

        testProduct = new LoanProduct();
        testProduct.setId(100L);
        testProduct.setName("Test Product");
        testProduct.setMinAmount(new BigDecimal("1000"));
        testProduct.setMaxAmount(new BigDecimal("10000"));
        testProduct.setMinTermMonths(6);
        testProduct.setMaxTermMonths(24);
        testProduct.setIsActive(true);

        userDetails = new UserDetailsImpl(testUser);
    }

    private void mockSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(testProfile));
    }

    @Test
    public void testCreateDraft_Success() {
        mockSecurityContext();
        when(loanProductRepository.findByIdAndIsActiveTrue(100L)).thenReturn(Optional.of(testProduct));
        
        LoanApplication savedApp = new LoanApplication();
        savedApp.setId(500L);
        savedApp.setProduct(testProduct);
        savedApp.setCustomer(testProfile);
        savedApp.setRequestedAmount(new BigDecimal("5000"));
        savedApp.setTermMonths(12);
        savedApp.setStatus(ApplicationStatus.DRAFT.name());
        
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(savedApp);

        CreateLoanApplicationRequest req = new CreateLoanApplicationRequest();
        req.setProductId(100L);
        req.setRequestedAmount(new BigDecimal("5000"));
        req.setTermMonths(12);

        LoanApplicationResponse resp = loanApplicationService.createDraft(req);

        assertNotNull(resp);
        assertEquals(500L, resp.getId());
        assertEquals("DRAFT", resp.getStatus());
        verify(statusHistoryRepository, times(1)).save(any(ApplicationStatusHistory.class));
        verify(auditService).recordAudit(eq("APPLICATION_CREATED"), eq("LoanApplication"), eq(500L), isNull(), anyString(), eq("system"));
    }

    @Test
    public void testCreateDraft_AmountBelowMin() {
        mockSecurityContext();
        when(loanProductRepository.findByIdAndIsActiveTrue(100L)).thenReturn(Optional.of(testProduct));

        CreateLoanApplicationRequest req = new CreateLoanApplicationRequest();
        req.setProductId(100L);
        req.setRequestedAmount(new BigDecimal("500"));
        req.setTermMonths(12);

        assertThrows(IllegalArgumentException.class, () -> loanApplicationService.createDraft(req));
    }

    @Test
    public void testCreateDraft_InactiveProduct() {
        mockSecurityContext();
        when(loanProductRepository.findByIdAndIsActiveTrue(100L)).thenReturn(Optional.empty());

        CreateLoanApplicationRequest req = new CreateLoanApplicationRequest();
        req.setProductId(100L);
        req.setRequestedAmount(new BigDecimal("5000"));
        req.setTermMonths(12);

        assertThrows(ResourceNotFoundException.class, () -> loanApplicationService.createDraft(req));
    }

    @Test
    public void testGetMyApplications_Success() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        Page<LoanApplication> page = new PageImpl<>(List.of(app));
        when(loanApplicationRepository.findByCustomerId(eq(10L), any(Pageable.class))).thenReturn(page);

        PaginatedResponse<LoanApplicationSummaryResponse> response = loanApplicationService.getMyApplications(0, 10);
        
        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(500L, response.getContent().get(0).getId());
    }

    @Test
    public void testGetMyApplicationById_Success() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));
        
        LoanApplicationResponse response = loanApplicationService.getMyApplicationById(500L);
        assertNotNull(response);
        assertEquals(500L, response.getId());
    }

    @Test
    public void testGetMyApplicationById_NotFoundOrUnowned() {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.empty());
        
        assertThrows(ResourceNotFoundException.class, () -> loanApplicationService.getMyApplicationById(500L));
    }

    @Test
    public void testUpdateDraft_Success() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        app.setStatus(ApplicationStatus.DRAFT.name());
        app.setRequestedAmount(new BigDecimal("2000"));
        app.setTermMonths(12);

        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(app);

        com.loanflow.loan.dto.UpdateLoanApplicationRequest req = new com.loanflow.loan.dto.UpdateLoanApplicationRequest();
        req.setRequestedAmount(new BigDecimal("3000"));
        req.setTermMonths(18);

        LoanApplicationResponse resp = loanApplicationService.updateDraft(500L, req);
        
        assertNotNull(resp);
        assertEquals(new BigDecimal("3000"), resp.getRequestedAmount());
        assertEquals(18, resp.getTermMonths());
    }

    @Test
    public void testUpdateDraft_AmountBelowMin() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        app.setStatus(ApplicationStatus.DRAFT.name());

        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));

        com.loanflow.loan.dto.UpdateLoanApplicationRequest req = new com.loanflow.loan.dto.UpdateLoanApplicationRequest();
        req.setRequestedAmount(new BigDecimal("500"));
        req.setTermMonths(12);

        assertThrows(IllegalArgumentException.class, () -> loanApplicationService.updateDraft(500L, req));
    }

    @Test
    public void testUpdateDraft_NotDraft() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        app.setStatus(ApplicationStatus.SUBMITTED.name());

        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));

        com.loanflow.loan.dto.UpdateLoanApplicationRequest req = new com.loanflow.loan.dto.UpdateLoanApplicationRequest();
        req.setRequestedAmount(new BigDecimal("2000"));
        req.setTermMonths(12);

        assertThrows(com.loanflow.exception.ResourceConflictException.class, () -> loanApplicationService.updateDraft(500L, req));
    }

    @Test
    public void testSubmitApplication_Success() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        app.setStatus(ApplicationStatus.DRAFT.name());

        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));
        when(loanApplicationRepository.save(any(LoanApplication.class))).thenReturn(app);

        LoanApplicationResponse resp = loanApplicationService.submitApplication(500L);

        assertEquals("SUBMITTED", resp.getStatus());
        verify(statusHistoryRepository, times(1)).save(any(ApplicationStatusHistory.class));
        verify(auditService).recordAudit(eq("APPLICATION_SUBMITTED"), eq("LoanApplication"), eq(500L), isNull(), anyString(), eq("system"));
    }

    @Test
    public void testSubmitApplication_NotDraft() {
        mockSecurityContext();
        LoanApplication app = new LoanApplication();
        app.setId(500L);
        app.setProduct(testProduct);
        app.setStatus("APPROVED");

        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(app));

        assertThrows(com.loanflow.exception.ResourceConflictException.class, () -> loanApplicationService.submitApplication(500L));
    }
}
