package com.cbs.form.repository;

import com.cbs.form.domain.FormShortcutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FormShortcutRepository extends JpaRepository<FormShortcutEntity, String> {

    List<FormShortcutEntity> findByIsActiveOrderByDisplayOrderAsc(String isActive);

    List<FormShortcutEntity> findByModuleAndIsActiveOrderByDisplayOrderAsc(String module, String isActive);

    @Query("SELECT s FROM FormShortcutEntity s WHERE s.isActive = 'Y' AND " +
           "(LOWER(s.formName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(s.keywords) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(s.formCode) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY s.displayOrder ASC")
    List<FormShortcutEntity> searchShortcuts(@Param("query") String query);
}
