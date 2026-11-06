package com.cbs.customer.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.DuplicateCustomerException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.domain.CustomerCatalog;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.dto.CustomerSearchCriteria;
import com.cbs.customer.repository.CustomerCatalogRepository;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.customer.repository.CustomerSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Customer Information File (CIF) Management Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerCatalogRepository customerCatalogRepository;
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

        // Validate customer type against Customer Catalog or CodeList
        validateCustomerType(command.customerType());
        validateCodeList("KYC_STATUS", command.kycStatus());
        validateCodeList("CUST_STATUS", command.status());

        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String cifNumber = generateCifNumber();

        Customer customer = Customer.builder()
                .cifNumber(cifNumber)
                .customerType(command.customerType())
                .fullName(command.fullName().trim())
                .gender(command.gender() != null ? command.gender().trim().toUpperCase() : "OTHER")
                .nationalId(command.nationalId().trim())
                .phoneNumber(command.phoneNumber() != null ? command.phoneNumber().trim() : null)
                .emailAddress(command.emailAddress() != null ? command.emailAddress().trim() : null)
                .taxNumber(command.taxNumber() != null ? command.taxNumber().trim() : null)
                .streetAddress(command.streetAddress() != null ? command.streetAddress().trim() : null)
                .commune(command.commune() != null ? command.commune().trim() : null)
                .district(command.district() != null ? command.district().trim() : null)
                .province(command.province() != null ? command.province().trim() : null)
                .country(command.country() != null ? command.country().trim() : "VN")
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

    /**
     * Searches customers matching dynamic keyword and advance criteria.
     */
    @Transactional(readOnly = true)
    public List<Customer> searchCustomers(CustomerSearchCriteria criteria) {
        Specification<Customer> spec = CustomerSpecification.buildSpecification(criteria);
        return customerRepository.findAll(spec);
    }

    /**
     * Returns all active customer types from customer catalog.
     */
    @Transactional(readOnly = true)
    public List<CustomerCatalog> getCustomerCatalog() {
        return customerCatalogRepository.findByIsActiveOrderByDisplayOrderAsc("Y");
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

    private void validateCustomerType(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) return;
        boolean inCatalog = customerCatalogRepository.existsById(typeCode.trim());
        if (!inCatalog) {
            // Fallback check in CODELIST CUST_TYPE
            if (!codeListService.isValid("CUST_TYPE", typeCode.trim())) {
                throw new IllegalArgumentException(
                        String.format("Invalid customer type '%s' in catalog", typeCode));
            }
        }
    }

    private void validateCodeList(String group, String value) {
        if (!codeListService.isValid(group, value)) {
            throw new IllegalArgumentException(
                    String.format("Invalid code '%s' for code group '%s'", value, group));
        }
    }
}
