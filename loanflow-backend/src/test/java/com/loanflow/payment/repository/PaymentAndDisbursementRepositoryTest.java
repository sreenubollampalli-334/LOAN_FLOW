package com.loanflow.payment.repository;

import com.loanflow.disbursement.repository.DisbursementAttemptRepository;
import com.loanflow.disbursement.repository.DisbursementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
    "spring.datasource.username=loanflow_user",
    "spring.datasource.password=loanflow_pass",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "loanflow.security.jwt.secret=testsecret123456789012345678901234",
    "loanflow.storage.s3.endpoint=http://localhost:9000",
    "loanflow.storage.s3.region=us-east-1",
    "loanflow.storage.s3.bucket=test",
    "loanflow.storage.s3.access-key=test",
    "loanflow.storage.s3.secret-key=test"
})
public class PaymentAndDisbursementRepositoryTest {

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private DisbursementAttemptRepository disbursementAttemptRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private RefundAllocationRepository refundAllocationRepository;

    @Test
    public void testRepositoriesLoad() {
        assertNotNull(disbursementRepository);
        assertNotNull(disbursementAttemptRepository);
        assertNotNull(paymentRepository);
        assertNotNull(paymentAllocationRepository);
        assertNotNull(refundAllocationRepository);
    }
}
