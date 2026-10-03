package com.loanflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    com.loanflow.LoanFlowApplication.class,
    SecurityConfigTest.TestController.class
}, properties = {
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
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {
        @GetMapping("/api/v1/auth/test")
        public String publicEndpoint() {
            return "Public";
        }

        @GetMapping("/api/v1/protected/test")
        public String protectedEndpoint() {
            return "Protected";
        }
    }

    @Test
    public void testPublicEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test"))
               .andExpect(status().isOk());
    }

    @Test
    public void testProtectedEndpointIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/protected/test"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"CUSTOMER"})
    public void testProtectedEndpointIsAccessibleWithAuth() throws Exception {
        mockMvc.perform(get("/api/v1/protected/test"))
               .andExpect(status().isOk());
    }
}
