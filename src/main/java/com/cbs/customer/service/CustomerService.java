package com.cbs.customer.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.DuplicateCustomerException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Customer Information File (CIF) Management Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final WorkingDateService workingDateService;
    private final CodeListService codeListService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Registers a new customer and generates a unique CIF number.
     */
    @Transactional
    public Customer createCustomer(CreateCustomerCommand command) {
        log.info("Creating customer profile for nationalId='{}', name='{}'",
                command.nationalId(), command.fullName());

        if (customerRepository.existsByNationalId(command.nationalId())) {
            throw new DuplicateCustomerException(
                    "Customer with nationalId '" + command.nationalId() + "' already exists");
        }

        // Validate code lists
        validateCodeList("CUST_TYPE", command.customerType());
        validateCodeList("KYC_STATUS", command.kycStatus());
        validateCodeList("CUST_STATUS", command.status());

        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String cifNumber = generateCifNumber();

        Customer customer = Customer.builder()
                .cifNumber(cifNumber)
                .customerType(command.customerType())
                .fullName(command.fullName().trim())
                .nationalId(command.nationalId().trim())
                .taxNumber(command.taxNumber() != null ? command.taxNumber().trim() : null)
                .kycStatus(command.kycStatus())
                .status(command.status())
                .createdWorkingDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .build();

        Customer saved = customerRepository.save(customer);
        log.info("Customer registered successfully with cifNumber='{}', customerId={}",
                saved.getCifNumber(), saved.getCustomerId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> CustomerNotFoundException.byId(customerId));
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByCif(String cifNumber) {
        return customerRepository.findByCifNumber(cifNumber)
                .orElseThrow(() -> CustomerNotFoundException.byCif(cifNumber));
    }

    @Transactional
    public Customer updateKycStatus(String cifNumber, String newKycStatus) {
        validateCodeList("KYC_STATUS", newKycStatus);
        Customer customer = getCustomerByCif(cifNumber);
        customer.setKycStatus(newKycStatus);
        log.info("Updated KYC status of customer '{}' to '{}'", cifNumber, newKycStatus);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomerStatus(String cifNumber, String newStatus) {
        validateCodeList("CUST_STATUS", newStatus);
        Customer customer = getCustomerByCif(cifNumber);
        customer.setStatus(newStatus);
        log.info("Updated status of customer '{}' to '{}'", cifNumber, newStatus);
        return customerRepository.save(customer);
    }

    private String generateCifNumber() {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        return String.format("CIF-%08d", seq);
    }

    private void validateCodeList(String group, String value) {
        if (!codeListService.isValid(group, value)) {
            throw new IllegalArgumentException(
                    String.format("Invalid code '%s' for code group '%s'", value, group));
        }
    }
}
