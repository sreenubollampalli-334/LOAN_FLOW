package com.loanflow.document.service;

import com.loanflow.document.dto.DocumentMetadataResponse;
import com.loanflow.document.entity.Document;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.storage.DocumentStorageProvider;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentStorageProvider storageProvider;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private ProductRequiredDocumentRepository requiredDocumentRepository;
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private DocumentService documentService;

    private User testUser;
    private CustomerProfile testProfile;
    private LoanProduct testProduct;
    private LoanApplication testApplication;
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

        testApplication = new LoanApplication();
        testApplication.setId(500L);
        testApplication.setCustomer(testProfile);
        testApplication.setProduct(testProduct);
        testApplication.setStatus(ApplicationStatus.DRAFT.name());

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
    public void testUploadDocument_Success() throws Exception {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setDocumentType("ID_PROOF");
        when(requiredDocumentRepository.findByProductIdAndDocumentType(100L, "ID_PROOF")).thenReturn(Optional.of(prd));

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        when(storageProvider.storeFile(any(), anyString())).thenReturn("stored-key");

        Document doc = new Document();
        doc.setId(999L);
        doc.setApplication(testApplication);
        doc.setCustomer(testProfile);
        doc.setDocumentType("ID_PROOF");
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize((long) "dummy content".length());
        doc.setUploadStatus("UPLOADED");
        doc.setCreatedAt(LocalDateTime.now());
        when(documentRepository.save(any(Document.class))).thenReturn(doc);

        DocumentMetadataResponse resp = documentService.uploadDocument(500L, "ID_PROOF", file);

        assertNotNull(resp);
        assertEquals(999L, resp.getId());
        assertEquals("test.pdf", resp.getOriginalFilename());
        verify(storageProvider, times(1)).storeFile(any(), anyString());
    }

    @Test
    public void testUploadDocument_InvalidStatus() {
        mockSecurityContext();
        testApplication.setStatus(ApplicationStatus.SUBMITTED.name());
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

        assertThrows(ResourceConflictException.class, () -> documentService.uploadDocument(500L, "ID_PROOF", file));
    }

    @Test
    public void testUploadDocument_NotRequired() {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        when(requiredDocumentRepository.findByProductIdAndDocumentType(100L, "NOT_REQUIRED")).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

        assertThrows(IllegalArgumentException.class, () -> documentService.uploadDocument(500L, "NOT_REQUIRED", file));
    }

    @Test
    public void testUploadDocument_UnsupportedType() {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        ProductRequiredDocument prd = new ProductRequiredDocument();
        when(requiredDocumentRepository.findByProductIdAndDocumentType(100L, "ID_PROOF")).thenReturn(Optional.of(prd));

        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/x-msdownload", "dummy".getBytes());

        assertThrows(IllegalArgumentException.class, () -> documentService.uploadDocument(500L, "ID_PROOF", file));
    }

    @Test
    public void testGetApplicationDocuments() {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        Document doc = new Document();
        doc.setId(999L);
        doc.setApplication(testApplication);
        doc.setCreatedAt(LocalDateTime.now());
        when(documentRepository.findByApplicationId(500L)).thenReturn(List.of(doc));

        List<DocumentMetadataResponse> list = documentService.getApplicationDocuments(500L);
        assertEquals(1, list.size());
        assertEquals(999L, list.get(0).getId());
    }
    
    @Test
    public void testDownloadDocument() throws Exception {
        mockSecurityContext();
        when(loanApplicationRepository.findByIdAndCustomerId(500L, 10L)).thenReturn(Optional.of(testApplication));
        
        Document doc = new Document();
        doc.setId(999L);
        doc.setApplication(testApplication);
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(10L);
        doc.setStorageKey("stored-key");
        
        when(documentRepository.findById(999L)).thenReturn(Optional.of(doc));
        
        InputStream is = new ByteArrayInputStream("dummy".getBytes());
        when(storageProvider.getFile("stored-key")).thenReturn(is);

        ResponseEntity<InputStreamResource> response = documentService.downloadDocument(500L, 999L);
        assertNotNull(response);
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
    }
}
