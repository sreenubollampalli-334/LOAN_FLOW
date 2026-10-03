package com.loanflow.review.dto;

import com.loanflow.eligibility.dto.ReasonCodeResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CreditReviewDetailsResponse {

    private ApplicationSummary application;
    private CustomerSummary customer;
    private ProductSummary product;
    private EligibilitySummary eligibilityAssessment;
    private List<DocumentSummary> documents;
    private CreditReviewResponse existingReview;

    public CreditReviewDetailsResponse() {}

    public ApplicationSummary getApplication() {
        return application;
    }

    public void setApplication(ApplicationSummary application) {
        this.application = application;
    }

    public CustomerSummary getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerSummary customer) {
        this.customer = customer;
    }

    public ProductSummary getProduct() {
        return product;
    }

    public void setProduct(ProductSummary product) {
        this.product = product;
    }

    public EligibilitySummary getEligibilityAssessment() {
        return eligibilityAssessment;
    }

    public void setEligibilityAssessment(EligibilitySummary eligibilityAssessment) {
        this.eligibilityAssessment = eligibilityAssessment;
    }

    public List<DocumentSummary> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentSummary> documents) {
        this.documents = documents;
    }

    public CreditReviewResponse getExistingReview() {
        return existingReview;
    }

    public void setExistingReview(CreditReviewResponse existingReview) {
        this.existingReview = existingReview;
    }

    public static class ApplicationSummary {
        private Long id;
        private String status;
        private BigDecimal requestedAmount;
        private Integer termMonths;
        private String purpose;
        private LocalDateTime createdAt;

        public ApplicationSummary() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getRequestedAmount() { return requestedAmount; }
        public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
        public Integer getTermMonths() { return termMonths; }
        public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class CustomerSummary {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;

        public CustomerSummary() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class ProductSummary {
        private Long id;
        private String name;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Integer minTermMonths;
        private Integer maxTermMonths;
        private BigDecimal interestRate;

        public ProductSummary() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getMinAmount() { return minAmount; }
        public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
        public BigDecimal getMaxAmount() { return maxAmount; }
        public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
        public Integer getMinTermMonths() { return minTermMonths; }
        public void setMinTermMonths(Integer minTermMonths) { this.minTermMonths = minTermMonths; }
        public Integer getMaxTermMonths() { return maxTermMonths; }
        public void setMaxTermMonths(Integer maxTermMonths) { this.maxTermMonths = maxTermMonths; }
        public BigDecimal getInterestRate() { return interestRate; }
        public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    }

    public static class EligibilitySummary {
        private Long id;
        private String decision;
        private String rulesetVersion;
        private LocalDateTime assessedAt;
        private List<ReasonCodeResponse> reasons;

        public EligibilitySummary() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }
        public String getRulesetVersion() { return rulesetVersion; }
        public void setRulesetVersion(String rulesetVersion) { this.rulesetVersion = rulesetVersion; }
        public LocalDateTime getAssessedAt() { return assessedAt; }
        public void setAssessedAt(LocalDateTime assessedAt) { this.assessedAt = assessedAt; }
        public List<ReasonCodeResponse> getReasons() { return reasons; }
        public void setReasons(List<ReasonCodeResponse> reasons) { this.reasons = reasons; }
    }

    public static class DocumentSummary {
        private Long id;
        private String documentType;
        private Boolean isMandatory;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String uploadStatus;
        private String reviewStatus;
        private LocalDateTime uploadedAt;

        public DocumentSummary() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public Boolean getIsMandatory() { return isMandatory; }
        public void setIsMandatory(Boolean isMandatory) { this.isMandatory = isMandatory; }
        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getUploadStatus() { return uploadStatus; }
        public void setUploadStatus(String uploadStatus) { this.uploadStatus = uploadStatus; }
        public String getReviewStatus() { return reviewStatus; }
        public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
        public LocalDateTime getUploadedAt() { return uploadedAt; }
        public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    }
}
