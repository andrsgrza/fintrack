package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetPeriodCloseWorkItem;
import com.fintrack.app.repository.BudgetPeriodCloseWorkItemRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Short transaction only: lease work without holding database locks during Analytics evaluation. */
@Service
public class BudgetPeriodCloseWorkClaimService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private final BudgetPeriodCloseWorkItemRepository workRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final int processingBatchSize;

    public BudgetPeriodCloseWorkClaimService(
        BudgetPeriodCloseWorkItemRepository workRepository,
        EntityManager entityManager,
        Clock clock,
        @Value("${application.budget-period-close.processing-batch-size:25}") int processingBatchSize
    ) {
        this.workRepository = workRepository;
        this.entityManager = entityManager;
        this.clock = clock;
        if (processingBatchSize <= 0) throw new IllegalArgumentException("Budget period-close processing batch size must be positive.");
        this.processingBatchSize = processingBatchSize;
    }

    @Transactional
    public List<Long> claimDueWork() {
        Instant now = clock.instant();
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager
            .createNativeQuery(
                "select id from budget_period_close_work_item " +
                "where (state in ('PENDING', 'RETRY_SCHEDULED') and next_attempt_at <= :now) " +
                "or (state = 'PROCESSING' and lease_until <= :now) " +
                "order by next_attempt_at asc, logical_closed_at asc, id asc limit :limit for update skip locked"
            )
            .setParameter("now", now)
            .setParameter("limit", processingBatchSize)
            .getResultList();
        Instant leaseUntil = now.plus(LEASE_DURATION);
        for (Number id : ids) {
            BudgetPeriodCloseWorkItem work = workRepository.findById(id.longValue()).orElse(null);
            if (work == null) continue;
            work.releaseExpiredLease(now);
            if (work.mayBeClaimedAt(now)) work.claim(leaseUntil);
        }
        return ids.stream().map(Number::longValue).toList();
    }
}
