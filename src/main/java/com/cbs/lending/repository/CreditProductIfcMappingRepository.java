package com.cbs.lending.repository;

import com.cbs.lending.domain.CreditProductIfcMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditProductIfcMappingRepository extends JpaRepository<CreditProductIfcMapping, Long> {
    List<CreditProductIfcMapping> findByProductCode(String productCode);
}
