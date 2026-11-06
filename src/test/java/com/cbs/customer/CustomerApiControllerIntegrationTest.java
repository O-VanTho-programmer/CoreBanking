package com.cbs.customer;

import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CustomerApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerService customerService;

    @Test
    @DisplayName("GET /api/v1/customers/catalogue: returns active customer types")
    void shouldReturnCustomerCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/customers/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].typeCode", is("INDIVIDUAL")))
                .andExpect(jsonPath("$[0].segment", is("RETAIL")));
    }

    @Test
    @DisplayName("POST & SEARCH /api/v1/customers: creates customer with extended fields and finds via search")
    void shouldCreateAndSearchCustomer() throws Exception {
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String nationalId = "ID-SEARCH-" + uniqueId;
        String phone = "090" + (int)(Math.random() * 9000000 + 1000000);
        String email = "search." + uniqueId + "@bank.com";

        CreateCustomerCommand command = CreateCustomerCommand.builder()
                .customerType("CORPORATE")
                .fullName("Corporate Tech " + uniqueId)
                .gender("OTHER")
                .nationalId(nationalId)
                .phoneNumber(phone)
                .emailAddress(email)
                .taxNumber("TAX-" + uniqueId)
                .streetAddress("456 Le Duan")
                .commune("Ben Nghe")
                .district("District 1")
                .province("Ho Chi Minh City")
                .country("VN")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build();

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cifNumber", notNullValue()))
                .andExpect(jsonPath("$.fullName", is("Corporate Tech " + uniqueId)))
                .andExpect(jsonPath("$.customerType", is("CORPORATE")));

        // Search by keyword (phone number)
        mockMvc.perform(get("/api/v1/customers/search")
                        .param("keyword", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].nationalId", is(nationalId)))
                .andExpect(jsonPath("$[0].phoneNumber", is(phone)));

        // Advance search by customerType & status
        mockMvc.perform(get("/api/v1/customers/search")
                        .param("customerType", "CORPORATE")
                        .param("status", "ACTIVE")
                        .param("fullName", uniqueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].nationalId", is(nationalId)));
    }
}
