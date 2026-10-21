package com.cbs.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table CUSTOMER (Customer Information File - CIF Master Record).
 */
@Entity
@Table(name = "CUSTOMER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "cif_number", length = 20, nullable = false, unique = true)
    private String cifNumber;

    @Column(name = "customer_type", length = 30, nullable = false)
    private String customerType;

    @Column(name = "full_name", length = 150, nullable = false)
    private String fullName;

    @Column(name = "national_id", length = 30, nullable = false, unique = true)
    private String nationalId;

    @Column(name = "tax_number", length = 30)
    private String taxNumber;

    @Column(name = "kyc_status", length = 30, nullable = false)
    @Builder.Default
    private String kycStatus = "PENDING";

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_working_date", nullable = false)
    private LocalDate createdWorkingDate;
}
