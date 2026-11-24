package com.fintrack.app.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import com.fintrack.app.domain.enumeration.BudgetPeriodCloseWorkItemState;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BudgetPeriodCloseWorkItemTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void transitionsOnlyFromClaimedWorkAndRetainsRetryEvidence() {
        BudgetPeriodCloseWorkItem work = pending();

        assertThat(work.mayBeClaimedAt(NOW)).isTrue();
        assertThatIllegalStateException().isThrownBy(() -> work.capture(outbox(), NOW));

        work.claim(NOW.plusSeconds(300));
        work.retry(NOW.plusSeconds(60), "transient Analytics failure");

        assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.RETRY_SCHEDULED);
        assertThat(work.getAttemptCount()).isEqualTo(1);
        assertThat(work.getLastError()).isEqualTo("transient Analytics failure");
        assertThat(work.getLeaseUntil()).isNull();
        assertThat(work.mayBeClaimedAt(NOW.plusSeconds(59))).isFalse();
        assertThat(work.mayBeClaimedAt(NOW.plusSeconds(60))).isTrue();

        work.claim(NOW.plusSeconds(360));
        work.capture(outbox(), NOW.plusSeconds(61));

        assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.CAPTURED);
        assertThat(work.getCapturedAt()).isEqualTo(NOW.plusSeconds(61));
        assertThat(work.getOutboxEvent()).isNotNull();
        assertThatIllegalStateException().isThrownBy(() -> work.retry(NOW.plusSeconds(62), "must not reopen"));
    }

    @Test
    void expiredLeaseBecomesClaimableButTerminalStatesDoNot() {
        BudgetPeriodCloseWorkItem work = pending();
        work.claim(NOW.plusSeconds(1));

        work.releaseExpiredLease(NOW.plusSeconds(1));

        assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.PENDING);
        assertThat(work.mayBeClaimedAt(NOW.plusSeconds(1))).isTrue();
        assertThat(work.getLastError()).contains("lease expired");

        work.claim(NOW.plusSeconds(30));
        work.cancel("notifications disabled");
        assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.CANCELLED);
        assertThatIllegalStateException().isThrownBy(() -> work.claim(NOW.plusSeconds(40)));
    }

    private BudgetPeriodCloseWorkItem pending() {
        return BudgetPeriodCloseWorkItem.pending(
            null,
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            "America/Mexico_City",
            NOW,
            NOW.minusSeconds(1)
        );
    }

    private BudgetNotificationOutbox outbox() {
        return BudgetNotificationOutbox.pending(null, null, BudgetNotificationType.PERIOD_CLOSED, "period-close-test", "{}", NOW);
    }
}
