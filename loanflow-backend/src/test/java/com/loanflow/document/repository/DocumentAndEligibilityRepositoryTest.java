package com.loanflow.document.repository;

import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.eligibility.repository.EligibilityReasonCodeRepository;
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
public class DocumentAndEligibilityRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentReviewRepository documentReviewRepository;

    @Autowired
    private EligibilityAssessmentRepository eligibilityAssessmentRepository;

    @Autowired
    private EligibilityReasonCodeRepository eligibilityReasonCodeRepository;

    @Test
    public void testRepositoriesLoad() {
        assertNotNull(documentRepository);
        assertNotNull(documentReviewRepository);
        assertNotNull(eligibilityAssessmentRepository);
        assertNotNull(eligibilityReasonCodeRepository);
    }
}
