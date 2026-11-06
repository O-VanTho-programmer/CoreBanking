package com.cbs.customer.dto;

import lombok.Builder;

/**
 * Filter criteria for customer profile inquiry and search.
 */
@Builder
public record CustomerSearchCriteria(
        String keyword,
        String customerType,
        String fullName,
        String nationalId,
        String phoneNumber,
        String emailAddress,
        String gender,
        String status,
        String kycStatus,
        String province
) {}
