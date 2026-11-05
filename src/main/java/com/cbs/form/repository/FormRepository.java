package com.cbs.form.repository;

import com.cbs.form.domain.FormEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FormRepository extends JpaRepository<FormEntity, String> {

    Optional<FormEntity> findByFormCodeAndIsActive(String formCode, String isActive);

    List<FormEntity> findByIsActiveOrderByFormCodeAsc(String isActive);

    List<FormEntity> findByModuleAndIsActiveOrderByFormCodeAsc(String module, String isActive);
}
