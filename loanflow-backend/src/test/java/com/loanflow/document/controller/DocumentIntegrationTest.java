package com.loanflow.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.document.entity.Document;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.storage.DocumentStorageProvider;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
        "spring.datasource.username=loanflow_user",
        "spring.datasource.password=loanflow_pass",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "loanflow.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "loanflow.storage.local.dir=target/test-storage"
})
@AutoConfigureMockMvc
@Transactional
public class DocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CustomerProfileRepository customerProfileRepository;
    @Autowired
    private LoanProductRepository loanProductRepository;
    @Autowired
    private ProductRequiredDocumentRepository requiredDocumentRepository;
    @Autowired
    private LoanApplicationRepository loanApplicationRepository;
    @Autowired
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentStorageProvider storageProvider;

    private User testUser;
    private CustomerProfile testProfile;
    private LoanProduct testProduct;
    private LoanApplication testApplication;
    private UserDetailsImpl userDetails;

    @BeforeEach
    public void setUp() {
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setEmail("doctest@example.com");
        testUser.setPasswordHash("hash");
        testUser.setIsActive(true);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
        userDetails = new UserDetailsImpl(testUser);

        testProfile = new CustomerProfile();
        testProfile.setUser(testUser);
        testProfile.setFirstName("Doc");
        testProfile.setLastName("Test");
        testProfile.setDob(LocalDate.of(1990, 1, 1));
        testProfile.setPhone("1234567890");
        testProfile = customerProfileRepository.save(testProfile);

        testProduct = new LoanProduct();
        testProduct.setName("Doc Loan Product " + System.currentTimeMillis());
        testProduct.setMinAmount(new BigDecimal("1000"));
        testProduct.setMaxAmount(new BigDecimal("50000"));
        testProduct.setInterestRate(new BigDecimal("0.05"));
        testProduct.setMinTermMonths(6);
        testProduct.setMaxTermMonths(60);
        testProduct.setIsActive(true);
        testProduct = loanProductRepository.save(testProduct);

        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setProduct(testProduct);
        prd.setDocumentType("ID_PROOF");
        prd.setIsMandatory(true);
        requiredDocumentRepository.save(prd);

        testApplication = new LoanApplication();
        testApplication.setCustomer(testProfile);
        testApplication.setProduct(testProduct);
        testApplication.setRequestedAmount(new BigDecimal("5000"));
        testApplication.setTermMonths(12);
        testApplication.setStatus(ApplicationStatus.DRAFT.name());
        testApplication = loanApplicationRepository.save(testApplication);
    }

    @Test
    public void testUploadDocument_Success() throws Exception {
        when(storageProvider.storeFile(any(), anyString())).thenReturn("stored-key");

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

        mockMvc.perform(multipart("/api/v1/loan-applications/" + testApplication.getId() + "/documents")
                .file(file)
                .param("documentType", "ID_PROOF")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"));
    }

    @Test
    public void testUploadDocument_InvalidStatus() throws Exception {
        testApplication.setStatus(ApplicationStatus.SUBMITTED.name());
        loanApplicationRepository.save(testApplication);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

        mockMvc.perform(multipart("/api/v1/loan-applications/" + testApplication.getId() + "/documents")
                .file(file)
                .param("documentType", "ID_PROOF")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testUploadDocument_WrongOwnerReturnsNotFound() throws Exception {
        User otherUser = new User();
        otherUser.setEmail("other@example.com");
        otherUser.setPasswordHash("hash");
        otherUser.setIsActive(true);
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        otherUser.setRoles(Set.of(customerRole));
        otherUser = userRepository.save(otherUser);

        CustomerProfile otherProfile = new CustomerProfile();
        otherProfile.setUser(otherUser);
        otherProfile.setFirstName("Other");
        otherProfile.setLastName("User");
        otherProfile.setDob(LocalDate.of(1990, 1, 1));
        otherProfile.setPhone("0987654321");
        customerProfileRepository.save(otherProfile);

        UserDetailsImpl otherDetails = new UserDetailsImpl(otherUser);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());

        mockMvc.perform(multipart("/api/v1/loan-applications/" + testApplication.getId() + "/documents")
                .file(file)
                .param("documentType", "ID_PROOF")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(otherDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDownloadDocument() throws Exception {
        Document doc = new Document();
        doc.setApplication(testApplication);
        doc.setCustomer(testProfile);
        doc.setDocumentType("ID_PROOF");
        doc.setOriginalFilename("test.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(5L);
        doc.setChecksum("checksum");
        doc.setStorageKey("key");
        doc.setUploadStatus("UPLOADED");
        doc = documentRepository.save(doc);

        when(storageProvider.getFile("key")).thenReturn(new ByteArrayInputStream("dummy".getBytes()));

        mockMvc.perform(get("/api/v1/loan-applications/" + testApplication.getId() + "/documents/" + doc.getId() + "/download")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"test.pdf\""))
                .andExpect(content().string("dummy"));
    }
}
