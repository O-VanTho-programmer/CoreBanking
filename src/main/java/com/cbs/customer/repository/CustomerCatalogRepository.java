package com.cbs.customer.repository;

import com.cbs.customer.domain.CustomerCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerCatalogRepository extends JpaRepository<CustomerCatalog, String> {

    List<CustomerCatalog> findByIsActiveOrderByDisplayOrderAsc(String isActive);
}
