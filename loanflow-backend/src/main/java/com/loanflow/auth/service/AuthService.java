package com.loanflow.auth.service;

import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.LoginRequest;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.auth.dto.UserResponse;
import com.loanflow.auth.exception.DuplicateEmailException;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.JwtService;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       CustomerProfileRepository customerProfileRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse registerCustomer(RegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException("Email is already registered");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not found"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.getRoles().add(customerRole);
        
        User savedUser = userRepository.save(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(savedUser);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDob(request.getDob());
        profile.setPhone(request.getPhone());
        
        customerProfileRepository.save(profile);

        UserDetailsImpl userDetails = new UserDetailsImpl(savedUser);
        String jwtToken = jwtService.generateToken(userDetails);
        
        return new AuthResponse(jwtToken, buildUserResponse(savedUser, profile));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
        User user = userDetails.getUser();
        
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new DisabledException("Account is disabled");
        }

        String jwtToken = jwtService.generateToken(userDetails);
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).orElse(null);

        return new AuthResponse(jwtToken, buildUserResponse(user, profile));
    }

    private UserResponse buildUserResponse(User user, CustomerProfile profile) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
        response.setRoles(roles);

        if (profile != null) {
            response.setFirstName(profile.getFirstName());
            response.setLastName(profile.getLastName());
        }
        
        return response;
    }
}
