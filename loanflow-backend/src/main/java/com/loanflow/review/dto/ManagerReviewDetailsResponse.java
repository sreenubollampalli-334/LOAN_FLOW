package com.loanflow.review.dto;

import com.loanflow.review.dto.CreditReviewDetailsResponse.*;
import java.util.List;

public class ManagerReviewDetailsResponse {

    private ApplicationSummary application;
    private CustomerSummary customer;
    private ProductSummary product;
    private EligibilitySummary eligibilityAssessment;
    private CreditReviewResponse creditOfficerReview;
    private List<DocumentSummary> documents;
    private ManagerReviewResponse existingManagerReview;

    public ManagerReviewDetailsResponse() {}

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

    public CreditReviewResponse getCreditOfficerReview() {
        return creditOfficerReview;
    }

    public void setCreditOfficerReview(CreditReviewResponse creditOfficerReview) {
        this.creditOfficerReview = creditOfficerReview;
    }

    public List<DocumentSummary> getDocuments() {
        return documents;
    }

    public void setDocuments(List<DocumentSummary> documents) {
        this.documents = documents;
    }

    public ManagerReviewResponse getExistingManagerReview() {
        return existingManagerReview;
    }

    public void setExistingManagerReview(ManagerReviewResponse existingManagerReview) {
        this.existingManagerReview = existingManagerReview;
    }
}
