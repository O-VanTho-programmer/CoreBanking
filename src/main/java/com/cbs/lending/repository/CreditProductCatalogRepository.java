package com.cbs.lending.repository;

import com.cbs.lending.domain.CreditProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditProductCatalogRepository extends JpaRepository<CreditProductCatalog, String> {

    Optional<CreditProductCatalog> findByProductCodeAndIsActive(String productCode, String isActive);
}
