package com.cbs.common.codelist.repository;

import com.cbs.common.codelist.domain.CodeList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeListRepository extends JpaRepository<CodeList, String> {
}
