package com.loanflow.product.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.product.dto.LoanProductDetailResponse;
import com.loanflow.product.dto.LoanProductSummaryResponse;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@org.springframework.boot.test.context.SpringBootTest(classes = {
        com.loanflow.product.service.LoanProductService.class,
        com.loanflow.product.service.LoanProductServiceCacheTest.CacheTestConfig.class,
        org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration.class
})
@ActiveProfiles("local")
public class LoanProductServiceCacheTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.cache.annotation.EnableCaching
    static class CacheTestConfig {
        @org.springframework.context.annotation.Bean("cacheManager")
        @org.springframework.context.annotation.Primary
        public CacheManager cacheManager() {
            return new org.springframework.cache.concurrent.ConcurrentMapCacheManager("loanProducts", "loanProductDetails");
        }
    }

    @Autowired
    private LoanProductService loanProductService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private LoanProductRepository loanProductRepository;

    @MockBean
    private ProductRequiredDocumentRepository documentRepository;

    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    private LoanProduct sampleProduct;

    @BeforeEach
    public void setup() {
        // Clear caches before each test
        cacheManager.getCache("loanProducts").clear();
        cacheManager.getCache("loanProductDetails").clear();

        sampleProduct = new LoanProduct();
        sampleProduct.setId(1L);
        sampleProduct.setName("Standard Personal Loan");
        sampleProduct.setDescription("A standard loan");
        sampleProduct.setMinAmount(new BigDecimal("1000"));
        sampleProduct.setMaxAmount(new BigDecimal("50000"));
        sampleProduct.setInterestRate(new BigDecimal("5.5"));
        sampleProduct.setMinTermMonths(12);
        sampleProduct.setMaxTermMonths(60);
        sampleProduct.setIsActive(true);
    }

    @Test
    public void testGetActiveProducts_CacheHitAndMiss() {
        Page<LoanProduct> page = new PageImpl<>(List.of(sampleProduct));
        when(loanProductRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page);

        // First call - Cache miss (Hits Repository)
        PaginatedResponse<LoanProductSummaryResponse> response1 = loanProductService.getActiveProducts(0, 10, "id", "ASC");
        assertThat(response1.getContent()).hasSize(1);
        verify(loanProductRepository, times(1)).findByIsActiveTrue(any(Pageable.class));

        // Second call - Cache hit (Should not hit Repository)
        PaginatedResponse<LoanProductSummaryResponse> response2 = loanProductService.getActiveProducts(0, 10, "id", "ASC");
        assertThat(response2.getContent()).hasSize(1);
        verify(loanProductRepository, times(1)).findByIsActiveTrue(any(Pageable.class)); // Still 1
    }

    @Test
    public void testGetActiveProductById_CacheHitAndMiss() {
        when(loanProductRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(sampleProduct));
        when(documentRepository.findByProductId(1L)).thenReturn(List.of());

        // First call - Cache miss
        LoanProductDetailResponse response1 = loanProductService.getActiveProductById(1L);
        assertThat(response1.getName()).isEqualTo("Standard Personal Loan");
        verify(loanProductRepository, times(1)).findByIdAndIsActiveTrue(1L);

        // Second call - Cache hit
        LoanProductDetailResponse response2 = loanProductService.getActiveProductById(1L);
        assertThat(response2.getName()).isEqualTo("Standard Personal Loan");
        verify(loanProductRepository, times(1)).findByIdAndIsActiveTrue(1L); // Still 1
    }

    @Test
    public void testCacheEviction() {
        Page<LoanProduct> page = new PageImpl<>(List.of(sampleProduct));
        when(loanProductRepository.findByIsActiveTrue(any(Pageable.class))).thenReturn(page);
        when(loanProductRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(sampleProduct));
        
        // Populate caches
        loanProductService.getActiveProducts(0, 10, "id", "ASC");
        loanProductService.getActiveProductById(1L);
        
        verify(loanProductRepository, times(1)).findByIsActiveTrue(any(Pageable.class));
        verify(loanProductRepository, times(1)).findByIdAndIsActiveTrue(1L);

        // Invalidate cache
        loanProductService.invalidateProductCache();

        // Call again - Should hit repository again
        loanProductService.getActiveProducts(0, 10, "id", "ASC");
        loanProductService.getActiveProductById(1L);

        verify(loanProductRepository, times(2)).findByIsActiveTrue(any(Pageable.class));
        verify(loanProductRepository, times(2)).findByIdAndIsActiveTrue(1L);
    }

    @Test
    public void testMultipleCachedPaginationKeys() {
        Page<LoanProduct> page1 = new PageImpl<>(List.of(sampleProduct));
        Page<LoanProduct> page2 = new PageImpl<>(List.of());

        when(loanProductRepository.findByIsActiveTrue(any(Pageable.class)))
                .thenReturn(page1) // First call
                .thenReturn(page2); // Second call

        PaginatedResponse<LoanProductSummaryResponse> response1 = loanProductService.getActiveProducts(0, 10, "id", "ASC");
        PaginatedResponse<LoanProductSummaryResponse> response2 = loanProductService.getActiveProducts(1, 10, "id", "ASC");

        assertThat(response1.getContent()).hasSize(1);
        assertThat(response2.getContent()).isEmpty();
        verify(loanProductRepository, times(2)).findByIsActiveTrue(any(Pageable.class));

        // Cache hit for both
        loanProductService.getActiveProducts(0, 10, "id", "ASC");
        loanProductService.getActiveProducts(1, 10, "id", "ASC");
        
        verify(loanProductRepository, times(2)).findByIsActiveTrue(any(Pageable.class)); // still 2
    }

    @Test
    public void testMutationFollowedByReadReturningFreshData() {
        when(loanProductRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(sampleProduct));
        when(documentRepository.findByProductId(1L)).thenReturn(List.of());

        LoanProductDetailResponse response1 = loanProductService.getActiveProductById(1L);
        assertThat(response1.getName()).isEqualTo("Standard Personal Loan");
        verify(loanProductRepository, times(1)).findByIdAndIsActiveTrue(1L);

        // Mutate the mock return value
        LoanProduct updatedProduct = new LoanProduct();
        updatedProduct.setId(1L);
        updatedProduct.setName("Updated Personal Loan");
        updatedProduct.setIsActive(true);
        when(loanProductRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(updatedProduct));

        // If we read now, we get stale data from cache
        LoanProductDetailResponse staleResponse = loanProductService.getActiveProductById(1L);
        assertThat(staleResponse.getName()).isEqualTo("Standard Personal Loan");
        verify(loanProductRepository, times(1)).findByIdAndIsActiveTrue(1L); // Not called

        // Trigger invalidation hook
        loanProductService.invalidateProductCache();

        // Read again, should hit DB and return fresh data
        LoanProductDetailResponse freshResponse = loanProductService.getActiveProductById(1L);
        assertThat(freshResponse.getName()).isEqualTo("Updated Personal Loan");
        verify(loanProductRepository, times(2)).findByIdAndIsActiveTrue(1L); // Called again
    }

    @Test
    public void testRedisUnavailableBehavior_ErrorHandlerSwallowsExceptions() {
        com.loanflow.config.RedisConfig redisConfig = new com.loanflow.config.RedisConfig();
        org.springframework.cache.interceptor.CacheErrorHandler errorHandler = redisConfig.errorHandler();
        
        // Verify that passing a RuntimeException doesn't rethrow (graceful fallback to DB)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            errorHandler.handleCacheGetError(new RuntimeException("Redis down"), null, "key");
            errorHandler.handleCachePutError(new RuntimeException("Redis down"), null, "key", "value");
            errorHandler.handleCacheEvictError(new RuntimeException("Redis down"), null, "key");
            errorHandler.handleCacheClearError(new RuntimeException("Redis down"), null);
        });
    }
}
