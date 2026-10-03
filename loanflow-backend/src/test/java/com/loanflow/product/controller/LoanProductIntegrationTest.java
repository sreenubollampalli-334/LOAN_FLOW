package com.loanflow.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
    "spring.datasource.username=loanflow_user",
    "spring.datasource.password=loanflow_pass",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "loanflow.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
    "loanflow.storage.s3.endpoint=http://localhost:9000",
    "loanflow.storage.s3.region=us-east-1",
    "loanflow.storage.s3.bucket=test",
    "loanflow.storage.s3.access-key=test",
    "loanflow.storage.s3.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
public class LoanProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanProductRepository loanProductRepository;

    private LoanProduct activeProduct;
    private LoanProduct inactiveProduct;

    @BeforeEach
    public void setUp() {
        activeProduct = new LoanProduct();
        activeProduct.setName("Active Loan " + System.currentTimeMillis());
        activeProduct.setMinAmount(new BigDecimal("1000"));
        activeProduct.setMaxAmount(new BigDecimal("10000"));
        activeProduct.setInterestRate(new BigDecimal("0.05"));
        activeProduct.setMinTermMonths(6);
        activeProduct.setMaxTermMonths(24);
        activeProduct.setIsActive(true);
        activeProduct = loanProductRepository.save(activeProduct);

        inactiveProduct = new LoanProduct();
        inactiveProduct.setName("Inactive Loan " + System.currentTimeMillis());
        inactiveProduct.setMinAmount(new BigDecimal("1000"));
        inactiveProduct.setMaxAmount(new BigDecimal("10000"));
        inactiveProduct.setInterestRate(new BigDecimal("0.05"));
        inactiveProduct.setMinTermMonths(6);
        inactiveProduct.setMaxTermMonths(24);
        inactiveProduct.setIsActive(false);
        inactiveProduct = loanProductRepository.save(inactiveProduct);
    }

    @Test
    public void testGetActiveProducts_Success() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    public void testGetActiveProducts_InvalidSort() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products?sortBy=invalidField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort field: invalidField"));
    }

    @Test
    public void testGetActiveProductById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products/" + activeProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(activeProduct.getName()));
    }

    @Test
    public void testGetActiveProductById_InactiveReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products/" + inactiveProduct.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetActiveProductById_MissingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/loan-products/999999999"))
                .andExpect(status().isNotFound());
    }
}
