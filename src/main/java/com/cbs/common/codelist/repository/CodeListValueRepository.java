package com.cbs.common.codelist.repository;

import com.cbs.common.codelist.domain.CodeListValue;
import com.cbs.common.codelist.domain.CodeListValueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodeListValueRepository extends JpaRepository<CodeListValue, CodeListValueId> {

    @Query("SELECT v.id.codeValue FROM CodeListValue v " +
           "WHERE v.id.codeGroup = :codeGroup AND v.isActive = 'Y' " +
           "ORDER BY v.sortOrder ASC")
    List<String> findActiveCodeValuesByCodeGroup(@Param("codeGroup") String codeGroup);

    @Query("SELECT v FROM CodeListValue v " +
           "WHERE v.id.codeGroup = :codeGroup AND v.isActive = 'Y' " +
           "ORDER BY v.sortOrder ASC")
    List<CodeListValue> findActiveEntitiesByCodeGroup(@Param("codeGroup") String codeGroup);

    @Query("SELECT DISTINCT v.id.codeGroup FROM CodeListValue v WHERE v.isActive = 'Y'")
    List<String> findAllActiveCodeGroups();
}
