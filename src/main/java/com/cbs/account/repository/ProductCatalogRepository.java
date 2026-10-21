package com.cbs.account.repository;

import com.cbs.account.domain.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductCatalogRepository extends JpaRepository<ProductCatalog, String> {

    Optional<ProductCatalog> findByProductCodeAndIsActive(String productCode, String isActive);
}
