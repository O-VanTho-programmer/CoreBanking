package com.cbs.account.repository;

import com.cbs.account.domain.Account;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AccountSpecification {

    public static Specification<Account> withCriteria(
            String keyword,
            String accountNumber,
            Set<Long> customerIdsFromKeywordOrName,
            String productCode,
            String status,
            String branchCode
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Quick search keyword
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase() + "%";
                Predicate accNumMatch = cb.like(cb.lower(root.get("accountNumber")), term);
                if (customerIdsFromKeywordOrName != null && !customerIdsFromKeywordOrName.isEmpty()) {
                    Predicate custMatch = root.get("customerId").in(customerIdsFromKeywordOrName);
                    predicates.add(cb.or(accNumMatch, custMatch));
                } else {
                    predicates.add(accNumMatch);
                }
            } else if (customerIdsFromKeywordOrName != null && !customerIdsFromKeywordOrName.isEmpty()) {
                predicates.add(root.get("customerId").in(customerIdsFromKeywordOrName));
            }

            if (accountNumber != null && !accountNumber.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("accountNumber")), "%" + accountNumber.trim().toLowerCase() + "%"));
            }

            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(root.get("productCode"), productCode.trim()));
            }

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }

            if (branchCode != null && !branchCode.isBlank()) {
                predicates.add(cb.equal(root.get("branchCode"), branchCode.trim()));
            }

            query.orderBy(cb.desc(root.get("accountId")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
