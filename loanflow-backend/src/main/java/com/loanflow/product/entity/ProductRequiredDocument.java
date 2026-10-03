package com.loanflow.product.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_required_documents", uniqueConstraints = {
    @UniqueConstraint(name = "unq_prd_product_doc", columnNames = {"product_id", "document_type"})
})
public class ProductRequiredDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private LoanProduct product;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(name = "is_mandatory", nullable = false)
    private Boolean isMandatory = true;

    public ProductRequiredDocument() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LoanProduct getProduct() { return product; }
    public void setProduct(LoanProduct product) { this.product = product; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public Boolean getIsMandatory() { return isMandatory; }
    public void setIsMandatory(Boolean isMandatory) { this.isMandatory = isMandatory; }
}
