package com.loanflow.product.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.product.dto.LoanProductDetailResponse;
import com.loanflow.product.dto.LoanProductSummaryResponse;
import com.loanflow.product.dto.RequiredDocumentResponse;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanProductService {

    private final LoanProductRepository loanProductRepository;
    private final ProductRequiredDocumentRepository documentRepository;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "id", "name", "minAmount", "maxAmount", "interestRate", "minTermMonths", "maxTermMonths"
    );

    public LoanProductService(LoanProductRepository loanProductRepository, ProductRequiredDocumentRepository documentRepository) {
        this.loanProductRepository = loanProductRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "loanProducts", key = "#page + '-' + #size + '-' + #sortBy + '-' + #direction", unless = "#result == null")
    public PaginatedResponse<LoanProductSummaryResponse> getActiveProducts(int page, int size, String sortBy, String direction) {
        if (size > 50) {
            size = 50;
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field: " + sortBy);
        }

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<LoanProduct> productPage = loanProductRepository.findByIsActiveTrue(pageable);

        List<LoanProductSummaryResponse> content = productPage.getContent().stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                content,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isFirst(),
                productPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "loanProductDetails", key = "#id", unless = "#result == null")
    public LoanProductDetailResponse getActiveProductById(Long id) {
        LoanProduct product = loanProductRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Active loan product not found with id: " + id));

        List<ProductRequiredDocument> documents = documentRepository.findByProductId(id);

        LoanProductDetailResponse response = mapToDetailResponse(product);
        response.setRequiredDocuments(documents.stream()
                .map(this::mapToRequiredDocumentResponse)
                .collect(Collectors.toList()));

        return response;
    }

    @Transactional
    @CacheEvict(value = {"loanProducts", "loanProductDetails"}, allEntries = true)
    public void invalidateProductCache() {
        // Explicit cache invalidation hook for product mutations
    }

    private LoanProductSummaryResponse mapToSummaryResponse(LoanProduct product) {
        LoanProductSummaryResponse response = new LoanProductSummaryResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setMinAmount(product.getMinAmount());
        response.setMaxAmount(product.getMaxAmount());
        response.setInterestRate(product.getInterestRate());
        response.setMinTermMonths(product.getMinTermMonths());
        response.setMaxTermMonths(product.getMaxTermMonths());
        return response;
    }

    private LoanProductDetailResponse mapToDetailResponse(LoanProduct product) {
        LoanProductDetailResponse response = new LoanProductDetailResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setMinAmount(product.getMinAmount());
        response.setMaxAmount(product.getMaxAmount());
        response.setInterestRate(product.getInterestRate());
        response.setMinTermMonths(product.getMinTermMonths());
        response.setMaxTermMonths(product.getMaxTermMonths());
        return response;
    }

    private RequiredDocumentResponse mapToRequiredDocumentResponse(ProductRequiredDocument doc) {
        RequiredDocumentResponse response = new RequiredDocumentResponse();
        response.setDocumentType(doc.getDocumentType());
        response.setIsMandatory(doc.getIsMandatory());
        return response;
    }
}
