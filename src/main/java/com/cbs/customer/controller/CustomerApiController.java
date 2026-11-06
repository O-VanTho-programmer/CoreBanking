package com.cbs.customer.controller;

import com.cbs.customer.domain.Customer;
import com.cbs.customer.domain.CustomerCatalog;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.dto.CustomerSearchCriteria;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CustomerApiController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createCustomer(@Valid @RequestBody CreateCustomerCommand command) {
        Customer customer = customerService.createCustomer(command);
        return ResponseEntity.ok(Map.of(
                "customerId", customer.getCustomerId(),
                "cifNumber", customer.getCifNumber(),
                "fullName", customer.getFullName(),
                "gender", customer.getGender(),
                "nationalId", customer.getNationalId(),
                "customerType", customer.getCustomerType(),
                "status", customer.getStatus()
        ));
    }

    @GetMapping("/catalogue")
    public ResponseEntity<List<CustomerCatalog>> getCustomerCatalog() {
        return ResponseEntity.ok(customerService.getCustomerCatalog());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Customer>> searchCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String nationalId,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String emailAddress,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kycStatus,
            @RequestParam(required = false) String province
    ) {
        CustomerSearchCriteria criteria = CustomerSearchCriteria.builder()
                .keyword(keyword)
                .customerType(customerType)
                .fullName(fullName)
                .nationalId(nationalId)
                .phoneNumber(phoneNumber)
                .emailAddress(emailAddress)
                .gender(gender)
                .status(status)
                .kycStatus(kycStatus)
                .province(province)
                .build();
        return ResponseEntity.ok(customerService.searchCustomers(criteria));
    }

    @GetMapping("/{cifNumber}")
    public ResponseEntity<Customer> getCustomerByCif(@PathVariable String cifNumber) {
        return customerRepository.findByCifNumber(cifNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
