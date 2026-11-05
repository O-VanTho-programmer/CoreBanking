package com.cbs.account.repository;

import com.cbs.account.domain.DepositIfcDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepositIfcDefinitionRepository extends JpaRepository<DepositIfcDefinition, String> {
    List<DepositIfcDefinition> findByIfcType(String ifcType);
}
