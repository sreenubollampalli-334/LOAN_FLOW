package com.loanflow.loan.repository;

import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import com.loanflow.review.repository.CreditReviewRepository;
import com.loanflow.review.repository.ManagerReviewRepository;
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
public class ReviewAndLoanRepositoryTest {

    @Autowired
    private CreditReviewRepository creditReviewRepository;

    @Autowired
    private ManagerReviewRepository managerReviewRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Test
    public void testRepositoriesLoad() {
        assertNotNull(creditReviewRepository);
        assertNotNull(managerReviewRepository);
        assertNotNull(loanRepository);
        assertNotNull(repaymentScheduleRepository);
    }
}
