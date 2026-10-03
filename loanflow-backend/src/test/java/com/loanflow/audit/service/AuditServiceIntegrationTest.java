package com.loanflow.audit.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
    "loanflow.storage.s3.access-key=minioadmin",
    "loanflow.storage.s3.secret-key=minioadmin",
    "loanflow.storage.s3.bucket-name=loanflow-documents-local"
})
public class AuditServiceIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Test
    public void testRecordAudit_FailsWithoutTransaction() {
        // Assert that calling recordAudit outside of an existing transaction throws an exception
        // due to @Transactional(propagation = Propagation.MANDATORY)
        assertThrows(IllegalTransactionStateException.class, () -> {
            auditService.recordAudit("TEST_ACTION", "TestEntity", 1L, null, "{\"data\":\"test\"}", "127.0.0.1");
        });
    }
}
