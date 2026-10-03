package com.loanflow.product.dto;

public class RequiredDocumentResponse {
    private String documentType;
    private Boolean isMandatory;

    public RequiredDocumentResponse() {}

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public Boolean getIsMandatory() { return isMandatory; }
    public void setIsMandatory(Boolean isMandatory) { this.isMandatory = isMandatory; }
}
