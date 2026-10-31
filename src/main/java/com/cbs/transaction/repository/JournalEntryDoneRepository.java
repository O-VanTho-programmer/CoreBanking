package com.cbs.transaction.repository;

import com.cbs.transaction.domain.JournalEntryDone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JournalEntryDoneRepository extends JpaRepository<JournalEntryDone, Long> {

    List<JournalEntryDone> findByTrxId(Long trxId);

    List<JournalEntryDone> findByAccountId(Long accountId);
}
