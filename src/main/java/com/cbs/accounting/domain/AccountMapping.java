package com.cbs.depositing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity mapping table ACCOUNT_MAPPING.
 * Defines abstract accounting roles (e.g., SYS_CASH_VAULT, SYS_FEE_INCOME).
 */
@Entity
@Table(name = "ACCOUNT_MAPPING")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMapping {

    @Id
    @Column(name = "mapping_id", length = 30, nullable = false)
    private String mappingId;

    @Column(name = "mapping_name", length = 100, nullable = false)
    private String mappingName;

    @Column(name = "account_type", length = 15, nullable = false)
    private String accountType;

    @Column(name = "description", length = 255)
    private String description;

    @OneToMany(mappedBy = "accountMapping", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AccountMappingDetail> details = new ArrayList<>();
}
