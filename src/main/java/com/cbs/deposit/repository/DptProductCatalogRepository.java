package com.cbs.deposit.repository;

import com.cbs.deposit.domain.DptProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DptProductCatalogRepository extends JpaRepository<DptProductCatalog, String> {

    Optional<DptProductCatalog> findByProductCodeAndIsActive(String productCode, String isActive);
}
