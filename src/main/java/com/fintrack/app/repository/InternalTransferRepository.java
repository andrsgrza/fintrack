package com.fintrack.app.repository;

import com.fintrack.app.domain.InternalTransfer;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the InternalTransfer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface InternalTransferRepository extends JpaRepository<InternalTransfer, Long> {}
