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
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageProvider storageProvider;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRequiredDocumentRepository requiredDocumentRepository;
    private final UserRepository userRepository;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    public DocumentService(DocumentRepository documentRepository,
                           DocumentStorageProvider storageProvider,
                           LoanApplicationRepository loanApplicationRepository,
                           CustomerProfileRepository customerProfileRepository,
                           ProductRequiredDocumentRepository requiredDocumentRepository,
                           UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.storageProvider = storageProvider;
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.userRepository = userRepository;
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return customerProfileRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    @Transactional
    public DocumentMetadataResponse uploadDocument(Long applicationId, String documentType, MultipartFile file) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        
        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.DRAFT.name().equals(application.getStatus()) &&
            !ApplicationStatus.RETURNED_FOR_REVIEW.name().equals(application.getStatus())) {
            throw new ResourceConflictException("Documents can only be uploaded when the application is in DRAFT or RETURNED_FOR_REVIEW status");
        }

        Optional<ProductRequiredDocument> requiredDoc = requiredDocumentRepository.findByProductIdAndDocumentType(
                application.getProduct().getId(), documentType);
        
        if (requiredDoc.isEmpty()) {
            throw new IllegalArgumentException("Document type not required for this loan product");
        }

        validateFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "document");
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String objectKey = UUID.randomUUID().toString() + "_" + originalFilename;
        String checksum;
        try {
            checksum = calculateChecksum(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to calculate checksum", e);
        }

        String storedKey = null;
        try {
            storedKey = storageProvider.storeFile(file, objectKey);
            
            Document document = new Document();
            document.setApplication(application);
            document.setCustomer(customer);
            document.setDocumentType(documentType);
            document.setOriginalFilename(originalFilename);
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setChecksum(checksum);
            document.setStorageKey(storedKey);
            document.setUploadStatus("UPLOADED");

            Document savedDocument = documentRepository.save(document);
            return mapToMetadataResponse(savedDocument);

        } catch (Exception e) {
            if (storedKey != null) {
                storageProvider.deleteFile(storedKey);
            }
            throw new RuntimeException("Failed to store document", e);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadataResponse> getApplicationDocuments(Long applicationId) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        List<Document> uploadedDocs = documentRepository.findByApplicationId(application.getId());
        uploadedDocs.sort(java.util.Comparator.comparing(Document::getCreatedAt).reversed());

        java.util.Map<String, Document> latestDocs = uploadedDocs.stream()
                .collect(Collectors.toMap(Document::getDocumentType, d -> d, (d1, d2) -> d1));

        return latestDocs.values().stream()
                .map(this::mapToMetadataResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentMetadataResponse getDocumentMetadata(Long applicationId, Long documentId) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getApplication().getId().equals(application.getId())) {
            throw new ResourceNotFoundException("Document not found in this application");
        }

        return mapToMetadataResponse(document);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> downloadDocument(Long applicationId, Long documentId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user;
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        } else {
            throw new ResourceNotFoundException("User not found in security context");
        }

        boolean isCreditOfficer = user.getRoles().stream()
                .anyMatch(r -> "CREDIT_OFFICER".equalsIgnoreCase(r.getName()) || "ROLE_CREDIT_OFFICER".equalsIgnoreCase(r.getName()));
        boolean isManager = user.getRoles().stream()
                .anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()) || "ROLE_MANAGER".equalsIgnoreCase(r.getName()));

        LoanApplication application;
        if (isCreditOfficer || isManager) {
            application = loanApplicationRepository.findById(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));
            if (!ApplicationStatus.SUBMITTED.name().equals(application.getStatus())) {
                throw new ResourceConflictException("Staff can only access documents for SUBMITTED applications");
            }
        } else {
            CustomerProfile customer = customerProfileRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
            application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customer.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getApplication().getId().equals(application.getId())) {
            throw new ResourceNotFoundException("Document not found in this application");
        }

        try {
            InputStream is = storageProvider.getFile(document.getStorageKey());
            InputStreamResource resource = new InputStreamResource(is);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getOriginalFilename() + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(document.getFileSize())
                    .contentType(MediaType.parseMediaType(document.getContentType()))
                    .body(resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download document", e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 5MB");
        }
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported file type");
        }
    }

    private String calculateChecksum(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private DocumentMetadataResponse mapToMetadataResponse(Document document) {
        DocumentMetadataResponse response = new DocumentMetadataResponse();
        response.setId(document.getId());
        response.setApplicationId(document.getApplication().getId());
        response.setDocumentType(document.getDocumentType());
        response.setOriginalFilename(document.getOriginalFilename());
        response.setContentType(document.getContentType());
        response.setFileSize(document.getFileSize());
        response.setUploadStatus(document.getUploadStatus());
        response.setUploadedAt(document.getCreatedAt());
        return response;
    }
}
