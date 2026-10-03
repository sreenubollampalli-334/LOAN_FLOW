package com.loanflow.profile.service;

import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.profile.dto.CustomerProfileResponse;
import com.loanflow.profile.dto.UpdateCustomerProfileRequest;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository customerProfileRepository;

    public CustomerProfileService(CustomerProfileRepository customerProfileRepository) {
        this.customerProfileRepository = customerProfileRepository;
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse getMyProfile() {
        CustomerProfile profile = getAuthenticatedCustomerProfile();
        return mapToResponse(profile);
    }

    @Transactional
    public CustomerProfileResponse updateMyProfile(UpdateCustomerProfileRequest request) {
        CustomerProfile profile = getAuthenticatedCustomerProfile();
        
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setPhone(request.getPhone());
        
        CustomerProfile updatedProfile = customerProfileRepository.save(profile);
        return mapToResponse(updatedProfile);
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = userDetails.getUser().getId();
        
        return customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private CustomerProfileResponse mapToResponse(CustomerProfile profile) {
        CustomerProfileResponse response = new CustomerProfileResponse();
        response.setId(profile.getId());
        response.setEmail(profile.getUser().getEmail());
        response.setFirstName(profile.getFirstName());
        response.setLastName(profile.getLastName());
        response.setDob(profile.getDob());
        response.setPhone(profile.getPhone());
        return response;
    }
}
