package com.loanflow.product.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.product.dto.LoanProductDetailResponse;
import com.loanflow.product.dto.LoanProductSummaryResponse;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LoanProductServiceTest {

    @Mock
    private LoanProductRepository loanProductRepository;

    @Mock
    private ProductRequiredDocumentRepository documentRepository;

    @InjectMocks
    private LoanProductService loanProductService;

    private LoanProduct testProduct;
    private ProductRequiredDocument testDocument;

    @BeforeEach
    public void setUp() {
        testProduct = new LoanProduct();
        testProduct.setId(1L);
        testProduct.setName("Personal Loan");
        testProduct.setDescription("A personal loan");
        testProduct.setMinAmount(new BigDecimal("1000"));
        testProduct.setMaxAmount(new BigDecimal("10000"));
        testProduct.setInterestRate(new BigDecimal("0.05"));
        testProduct.setMinTermMonths(6);
        testProduct.setMaxTermMonths(24);
        testProduct.setIsActive(true);

        testDocument = new ProductRequiredDocument();
        testDocument.setId(10L);
        testDocument.setProduct(testProduct);
        testDocument.setDocumentType("ID_PROOF");
        testDocument.setIsMandatory(true);
    }

    @Test
    public void testGetActiveProducts_Success() {
        Page<LoanProduct> page = new PageImpl<>(List.of(testProduct));
        when(loanProductRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page);

        PaginatedResponse<LoanProductSummaryResponse> response = loanProductService.getActiveProducts(0, 10, "id", "ASC");

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Personal Loan", response.getContent().get(0).getName());
        assertTrue(response.isLast());
        assertTrue(response.isFirst());
    }

    @Test
    public void testGetActiveProducts_InvalidSort() {
        assertThrows(IllegalArgumentException.class, () -> loanProductService.getActiveProducts(0, 10, "invalidField", "ASC"));
    }

    @Test
    public void testGetActiveProductById_Success() {
        when(loanProductRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(testProduct));
        when(documentRepository.findByProductId(1L)).thenReturn(List.of(testDocument));

        LoanProductDetailResponse response = loanProductService.getActiveProductById(1L);

        assertNotNull(response);
        assertEquals("Personal Loan", response.getName());
        assertEquals(1, response.getRequiredDocuments().size());
        assertEquals("ID_PROOF", response.getRequiredDocuments().get(0).getDocumentType());
    }

    @Test
    public void testGetActiveProductById_NotFound() {
        when(loanProductRepository.findByIdAndIsActiveTrue(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> loanProductService.getActiveProductById(99L));
    }
}
