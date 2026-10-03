package com.loanflow.profile.controller;

import com.loanflow.profile.dto.CustomerProfileResponse;
import com.loanflow.profile.dto.UpdateCustomerProfileRequest;
import com.loanflow.profile.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final CustomerProfileService customerProfileService;

    public CustomerProfileController(CustomerProfileService customerProfileService) {
        this.customerProfileService = customerProfileService;
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerProfileResponse> getMyProfile() {
        return ResponseEntity.ok(customerProfileService.getMyProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerProfileResponse> updateMyProfile(
            @Valid @RequestBody UpdateCustomerProfileRequest request) {
        return ResponseEntity.ok(customerProfileService.updateMyProfile(request));
    }
}
