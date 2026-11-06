package com.cbs.customer.controller;

import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                "nationalId", customer.getNationalId(),
                "status", customer.getStatus()
        ));
    }

    @GetMapping("/{cifNumber}")
    public ResponseEntity<Customer> getCustomerByCif(@PathVariable String cifNumber) {
        return customerRepository.findByCifNumber(cifNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
