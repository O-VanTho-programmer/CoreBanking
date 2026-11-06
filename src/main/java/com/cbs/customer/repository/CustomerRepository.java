package com.cbs.customer.repository;

import com.cbs.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByCifNumber(String cifNumber);

    Optional<Customer> findByNationalId(String nationalId);

    boolean existsByNationalId(String nationalId);

    boolean existsByCifNumber(String cifNumber);
}
