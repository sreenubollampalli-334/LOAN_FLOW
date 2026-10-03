package com.loanflow.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.profile.dto.UpdateCustomerProfileRequest;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
public class CustomerProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    private ObjectMapper objectMapper;

    @Autowired
    private com.loanflow.security.JwtService jwtService;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        if (roleRepository.findByName("CUSTOMER").isEmpty()) {
            Role role = new Role();
            role.setName("CUSTOMER");
            roleRepository.save(role);
        }
        
        if (roleRepository.findByName("MANAGER").isEmpty()) {
            Role role = new Role();
            role.setName("MANAGER");
            roleRepository.save(role);
        }
    }

    private String createUserAndGetToken(String email, String roleName) throws Exception {
        com.loanflow.auth.dto.RegistrationRequest regRequest = new com.loanflow.auth.dto.RegistrationRequest();
        regRequest.setFirstName("TestFirst");
        regRequest.setLastName("TestLast");
        regRequest.setEmail(email);
        regRequest.setPassword("securePassword123");
        regRequest.setDob(LocalDate.of(1995, 5, 15));
        regRequest.setPhone("9876543210");

        String responseStr = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
                .andReturn().getResponse().getContentAsString();

        com.loanflow.auth.dto.AuthResponse authResponse = objectMapper.readValue(responseStr, com.loanflow.auth.dto.AuthResponse.class);
        
        if (!roleName.equals("CUSTOMER")) {
            User user = userRepository.findByEmail(email).get();
            Role newRole = roleRepository.findByName(roleName).get();
            user.getRoles().clear();
            user.getRoles().add(newRole);
            userRepository.save(user);
        }
        
        return authResponse.getToken();
    }

    @Test
    public void testGetMyProfile_Success() throws Exception {
        String token = createUserAndGetToken("customer_prof@example.com", "CUSTOMER");

        mockMvc.perform(get("/api/v1/customers/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer_prof@example.com"))
                .andExpect(jsonPath("$.firstName").value("TestFirst"));
    }

    @Test
    public void testGetMyProfile_NotFound() throws Exception {
        // Create user directly without profile
        Role customerRole = roleRepository.findByName("CUSTOMER").get();
        User user = new User();
        user.setEmail("noprofile@example.com");
        user.setPasswordHash("hash");
        user.setIsActive(true);
        user.getRoles().add(customerRole);
        user = userRepository.save(user);
        
        com.loanflow.security.UserDetailsImpl userDetails = new com.loanflow.security.UserDetailsImpl(user);
        String token = jwtService.generateToken(userDetails);

        mockMvc.perform(get("/api/v1/customers/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateMyProfile_Success() throws Exception {
        String token = createUserAndGetToken("update_prof@example.com", "CUSTOMER");

        UpdateCustomerProfileRequest updateRequest = new UpdateCustomerProfileRequest();
        updateRequest.setFirstName("UpdatedFirst");
        updateRequest.setLastName("UpdatedLast");
        updateRequest.setPhone("1112223333");

        mockMvc.perform(put("/api/v1/customers/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.lastName").value("UpdatedLast"))
                .andExpect(jsonPath("$.phone").value("1112223333"))
                .andExpect(jsonPath("$.email").value("update_prof@example.com"));
    }

    @Test
    public void testAccessDeniedForNonCustomer() throws Exception {
        String token = createUserAndGetToken("manager_prof@example.com", "MANAGER");

        mockMvc.perform(get("/api/v1/customers/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/customers/me"))
                .andExpect(status().isUnauthorized());
    }
}
