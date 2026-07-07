package com.fintrack.app.repository;

import com.fintrack.app.domain.IngestionRecord;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the IngestionRecord entity.
 */
@SuppressWarnings("unused")
@Repository
public interface IngestionRecordRepository extends JpaRepository<IngestionRecord, Long>, JpaSpecificationExecutor<IngestionRecord> {}
