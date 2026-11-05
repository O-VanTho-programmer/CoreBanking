package com.cbs.lending.repository;

import com.cbs.lending.domain.CreditIfcDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditIfcDefinitionRepository extends JpaRepository<CreditIfcDefinition, String> {
    List<CreditIfcDefinition> findByIfcType(String ifcType);
}
