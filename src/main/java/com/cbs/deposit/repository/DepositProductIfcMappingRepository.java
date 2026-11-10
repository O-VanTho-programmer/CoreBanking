package com.cbs.deposit.repository;

import com.cbs.deposit.domain.DepositProductIfcMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepositProductIfcMappingRepository extends JpaRepository<DepositProductIfcMapping, Long> {
    List<DepositProductIfcMapping> findByProductCode(String productCode);
}
