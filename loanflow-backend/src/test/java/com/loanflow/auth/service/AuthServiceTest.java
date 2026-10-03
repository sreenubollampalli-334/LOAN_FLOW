package com.loanflow.auth.service;

import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.LoginRequest;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.auth.exception.DuplicateEmailException;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.JwtService;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegistrationRequest registrationRequest;
    private LoginRequest loginRequest;
    private User testUser;
    private Role customerRole;

    @BeforeEach
    public void setUp() {
        registrationRequest = new RegistrationRequest();
        registrationRequest.setFirstName("John");
        registrationRequest.setLastName("Doe");
        registrationRequest.setEmail("john@example.com");
        registrationRequest.setPassword("securePassword123");
        registrationRequest.setDob(LocalDate.of(1990, 1, 1));
        registrationRequest.setPhone("1234567890");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("john@example.com");
        loginRequest.setPassword("securePassword123");

        customerRole = new Role();
        customerRole.setId(1);
        customerRole.setName("CUSTOMER");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("john@example.com");
        testUser.setPasswordHash("hashed_password");
        testUser.setIsActive(true);
        testUser.getRoles().add(customerRole);
    }

    @Test
    public void testRegisterCustomer_Success() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(UserDetailsImpl.class))).thenReturn("jwt-token");

        AuthResponse response = authService.registerCustomer(registrationRequest);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("john@example.com", response.getUser().getEmail());
        assertTrue(response.getUser().getRoles().contains("CUSTOMER"));

        verify(userRepository, times(1)).save(any(User.class));
        verify(customerProfileRepository, times(1)).save(any(CustomerProfile.class));
    }

    @Test
    public void testRegisterCustomer_DuplicateEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        assertThrows(DuplicateEmailException.class, () -> authService.registerCustomer(registrationRequest));

        verify(userRepository, never()).save(any(User.class));
        verify(customerProfileRepository, never()).save(any(CustomerProfile.class));
    }

    @Test
    public void testLogin_Success() {
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        
        UserDetailsImpl userDetails = new UserDetailsImpl(testUser);
        when(auth.getPrincipal()).thenReturn(userDetails);
        
        when(jwtService.generateToken(any(UserDetailsImpl.class))).thenReturn("jwt-token");
        when(customerProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("jwt-token", response.getToken());
        assertEquals("john@example.com", response.getUser().getEmail());
    }

    @Test
    public void testLogin_InvalidCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
    }

    @Test
    public void testLogin_DisabledAccount() {
        testUser.setIsActive(false);
        
        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        
        UserDetailsImpl userDetails = new UserDetailsImpl(testUser);
        when(auth.getPrincipal()).thenReturn(userDetails);

        assertThrows(DisabledException.class, () -> authService.login(loginRequest));
    }
}
