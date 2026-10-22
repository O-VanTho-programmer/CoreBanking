package com.cbs.transaction.repository;

import com.cbs.transaction.domain.JournalEntryActive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryActiveRepository extends JpaRepository<JournalEntryActive, Long> {

    List<JournalEntryActive> findByTrxIdOrderByEntryGroupAscEntryIndexAsc(Long trxId);

    List<JournalEntryActive> findByAccountId(Long accountId);
}
