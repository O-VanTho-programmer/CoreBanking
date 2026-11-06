package com.cbs.customer.repository;

import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CustomerSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CustomerSpecification {

    public static Specification<Customer> buildSpecification(CustomerSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria == null) {
                return cb.conjunction();
            }

            // Quick Search Keyword (CIF, Full Name, National ID, Phone)
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().trim().toUpperCase() + "%";
                Predicate matchCif = cb.like(cb.upper(root.get("cifNumber")), pattern);
                Predicate matchName = cb.like(cb.upper(root.get("fullName")), pattern);
                Predicate matchNationalId = cb.like(cb.upper(root.get("nationalId")), pattern);
                Predicate matchPhone = cb.like(root.get("phoneNumber"), "%" + criteria.keyword().trim() + "%");
                predicates.add(cb.or(matchCif, matchName, matchNationalId, matchPhone));
            }

            // Advance Filter: Customer Type
            if (criteria.customerType() != null && !criteria.customerType().isBlank() && !"ALL".equalsIgnoreCase(criteria.customerType())) {
                predicates.add(cb.equal(root.get("customerType"), criteria.customerType().trim()));
            }

            // Advance Filter: Full Name
            if (criteria.fullName() != null && !criteria.fullName().isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("fullName")), "%" + criteria.fullName().trim().toUpperCase() + "%"));
            }

            // Advance Filter: National ID
            if (criteria.nationalId() != null && !criteria.nationalId().isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("nationalId")), "%" + criteria.nationalId().trim().toUpperCase() + "%"));
            }

            // Advance Filter: Phone Number
            if (criteria.phoneNumber() != null && !criteria.phoneNumber().isBlank()) {
                predicates.add(cb.like(root.get("phoneNumber"), "%" + criteria.phoneNumber().trim() + "%"));
            }

            // Advance Filter: Email Address
            if (criteria.emailAddress() != null && !criteria.emailAddress().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("emailAddress")), "%" + criteria.emailAddress().trim().toLowerCase() + "%"));
            }

            // Advance Filter: Gender
            if (criteria.gender() != null && !criteria.gender().isBlank() && !"ALL".equalsIgnoreCase(criteria.gender())) {
                predicates.add(cb.equal(root.get("gender"), criteria.gender().trim().toUpperCase()));
            }

            // Advance Filter: Status
            if (criteria.status() != null && !criteria.status().isBlank() && !"ALL".equalsIgnoreCase(criteria.status())) {
                predicates.add(cb.equal(root.get("status"), criteria.status().trim().toUpperCase()));
            }

            // Advance Filter: KYC Status
            if (criteria.kycStatus() != null && !criteria.kycStatus().isBlank() && !"ALL".equalsIgnoreCase(criteria.kycStatus())) {
                predicates.add(cb.equal(root.get("kycStatus"), criteria.kycStatus().trim().toUpperCase()));
            }

            // Advance Filter: Province
            if (criteria.province() != null && !criteria.province().isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("province")), "%" + criteria.province().trim().toUpperCase() + "%"));
            }

            // Order by customerId descending
            query.orderBy(cb.desc(root.get("customerId")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
