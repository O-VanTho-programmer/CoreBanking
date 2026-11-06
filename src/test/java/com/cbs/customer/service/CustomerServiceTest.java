package com.cbs.customer.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.DuplicateCustomerException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.repository.CustomerCatalogRepository;
import com.cbs.customer.repository.CustomerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCatalogRepository customerCatalogRepository;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private CodeListService codeListService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CustomerService customerService;

    private final LocalDate workingDate = LocalDate.of(2026, 10, 21);

    @Test
    @DisplayName("createCustomer(): successfully registers customer with generated CIF number")
    void shouldCreateCustomerSuccessfully() {
        CreateCustomerCommand command = CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Nguyen Van A")
                .nationalId("001200001234")
                .kycStatus("PENDING")
                .status("ACTIVE")
                .build();

        when(customerRepository.existsByNationalId("001200001234")).thenReturn(false);
        when(codeListService.isValid("CUST_TYPE", "INDIVIDUAL")).thenReturn(true);
        when(codeListService.isValid("KYC_STATUS", "PENDING")).thenReturn(true);
        when(codeListService.isValid("CUST_STATUS", "ACTIVE")).thenReturn(true);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(10000001L);

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setCustomerId(101L);
            return c;
        });

        Customer result = customerService.createCustomer(command);

        assertThat(result.getCustomerId()).isEqualTo(101L);
        assertThat(result.getCifNumber()).isEqualTo("CIF-10000001");
        assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(result.getCreatedWorkingDate()).isEqualTo(workingDate);
    }

    @Test
    @DisplayName("createCustomer(): throws DuplicateCustomerException when nationalId exists")
    void shouldThrowDuplicateWhenNationalIdExists() {
        CreateCustomerCommand command = CreateCustomerCommand.builder()
                .fullName("Nguyen Van B")
                .nationalId("001200009999")
                .build();

        when(customerRepository.existsByNationalId("001200009999")).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(command))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("001200009999");
    }

    @Test
    @DisplayName("updateKycStatus(): updates status to VERIFIED when valid")
    void shouldUpdateKycStatus() {
        Customer existing = Customer.builder()
                .customerId(101L).cifNumber("CIF-10000001").kycStatus("PENDING").build();

        when(codeListService.isValid("KYC_STATUS", "VERIFIED")).thenReturn(true);
        when(customerRepository.findByCifNumber("CIF-10000001")).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer updated = customerService.updateKycStatus("CIF-10000001", "VERIFIED");

        assertThat(updated.getKycStatus()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("getCustomerByCif(): throws CustomerNotFoundException when CIF does not exist")
    void shouldThrowNotFoundForInvalidCif() {
        when(customerRepository.findByCifNumber("CIF-99999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getCustomerByCif("CIF-99999999"))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining("CIF-99999999");
    }
}
