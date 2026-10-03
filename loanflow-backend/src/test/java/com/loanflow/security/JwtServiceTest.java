package com.loanflow.security;

import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class JwtServiceTest {

    private JwtService jwtService;
    private UserDetailsImpl userDetails;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour

        Role role = new Role();
        role.setName("CUSTOMER");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed_password");
        user.setIsActive(true);
        user.getRoles().add(role);

        userDetails = new UserDetailsImpl(user);
    }

    @Test
    public void testGenerateTokenAndExtractUsername() {
        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);

        String username = jwtService.extractUsername(token);
        assertEquals("test@example.com", username);
    }

    @Test
    public void testIsTokenValid() {
        String token = jwtService.generateToken(userDetails);
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    public void testIsTokenExpired() {
        // Create an expired token by overriding expiration to -1000 ms
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);
        String token = jwtService.generateToken(userDetails);
        
        try {
            jwtService.isTokenExpired(token);
            fail("Expected ExpiredJwtException");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            assertTrue(e.getMessage().contains("JWT expired"));
        }
    }
}
