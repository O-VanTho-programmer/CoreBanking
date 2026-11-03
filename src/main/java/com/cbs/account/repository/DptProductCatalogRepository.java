package com.cbs.account.repository;

import com.cbs.account.domain.DptProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DptProductCatalogRepository extends JpaRepository<DptProductCatalog, String> {

    Optional<DptProductCatalog> findByProductCodeAndIsActive(String productCode, String isActive);
}
