package com.fintrack.app.repository;

import com.fintrack.app.domain.FileIngestion;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the FileIngestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FileIngestionRepository extends JpaRepository<FileIngestion, Long> {}
