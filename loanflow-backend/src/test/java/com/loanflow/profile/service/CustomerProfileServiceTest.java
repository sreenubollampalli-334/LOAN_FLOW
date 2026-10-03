package com.loanflow.profile.service;

import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.profile.dto.CustomerProfileResponse;
import com.loanflow.profile.dto.UpdateCustomerProfileRequest;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerProfileServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomerProfileService customerProfileService;

    private User testUser;
    private CustomerProfile testProfile;

    @BeforeEach
    public void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");

        testProfile = new CustomerProfile();
        testProfile.setId(10L);
        testProfile.setUser(testUser);
        testProfile.setFirstName("Alice");
        testProfile.setLastName("Wonderland");
        testProfile.setDob(LocalDate.of(1990, 1, 1));
        testProfile.setPhone("1234567890");

        UserDetailsImpl userDetails = new UserDetailsImpl(testUser);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    public void testGetMyProfile_Success() {
        when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(testProfile));

        CustomerProfileResponse response = customerProfileService.getMyProfile();

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Alice", response.getFirstName());
        assertEquals(10L, response.getId());
    }

    @Test
    public void testGetMyProfile_NotFound() {
        when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerProfileService.getMyProfile());
    }

    @Test
    public void testUpdateMyProfile_Success() {
        when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(testProfile));
        when(customerProfileRepository.save(any(CustomerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();
        request.setFirstName("Bob");
        request.setLastName("Builder");
        request.setPhone("0987654321");

        CustomerProfileResponse response = customerProfileService.updateMyProfile(request);

        assertNotNull(response);
        assertEquals("Bob", response.getFirstName());
        assertEquals("Builder", response.getLastName());
        assertEquals("0987654321", response.getPhone());
        assertEquals("test@example.com", response.getEmail()); // Email remains unchanged
        assertEquals(LocalDate.of(1990, 1, 1), response.getDob()); // Dob remains unchanged
        
        verify(customerProfileRepository, times(1)).save(testProfile);
    }

    @Test
    public void testUpdateMyProfile_NotFound() {
        when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UpdateCustomerProfileRequest request = new UpdateCustomerProfileRequest();
        request.setFirstName("Bob");
        request.setLastName("Builder");
        request.setPhone("0987654321");

        assertThrows(ResourceNotFoundException.class, () -> customerProfileService.updateMyProfile(request));
        verify(customerProfileRepository, never()).save(any(CustomerProfile.class));
    }
}
