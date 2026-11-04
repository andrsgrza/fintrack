package com.fintrack.app.service.analytics;

import java.util.EnumSet;
import java.util.Set;

/**
 * The entire field vocabulary of the vertical proof. Names identify financial concepts, never persistence paths.
 */
public enum SemanticField {
    TRANSACTION_ID(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    TRANSACTION_AMOUNT(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    TRANSACTION_FLOW(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    TRANSACTION_DATE(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Date-only transaction bucket; context selects the range while this field partitions it by calendar day. */
    TRANSACTION_DAY(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** ISO week-year transaction bucket, for example {@code 2026-W01}. */
    TRANSACTION_WEEK(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Calendar month transaction bucket, for example {@code 2026-01}. */
    TRANSACTION_MONTH(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Calendar-year transaction bucket, for example {@code 2026}. */
    TRANSACTION_YEAR(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    TRANSFER_LINK_STATUS(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    ACCOUNT_INSTITUTION(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Exact assigned category identity; its label is returned as dimension metadata. */
    CATEGORY_ID(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** The ultimate ancestor of an assigned category. */
    CATEGORY_ROOT(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** The first category underneath the ultimate root. */
    CATEGORY_SUBCATEGORY(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Immediate branch under the category selected by a DESCENDANT_OF condition. */
    CATEGORY_BRANCH_CHILD(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    /** Tag identity; grouping intentionally has overlapping, non-additive semantics. */
    TAG_ID(Set.of(SemanticDataset.TRANSACTION_LEDGER)),
    ACCOUNT_TYPE(
        EnumSet.of(SemanticDataset.TRANSACTION_LEDGER, SemanticDataset.ACCOUNT_POSITION_SNAPSHOT, SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)
    ),
    ACCOUNT_ID(
        EnumSet.of(SemanticDataset.TRANSACTION_LEDGER, SemanticDataset.ACCOUNT_POSITION_SNAPSHOT, SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)
    ),
    OBSERVATION_DATE(
        EnumSet.of(
            SemanticDataset.FINANCIAL_POSITION_SNAPSHOT,
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT
        )
    ),
    OBSERVATION_MONTH(EnumSet.of(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT, SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)),
    POSITION_BALANCE(Set.of(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT)),
    /** Assets less recorded credit-card debt, partitioned by currency at one observation cutoff. */
    FINANCIAL_POSITION(Set.of(SemanticDataset.FINANCIAL_POSITION_SNAPSHOT)),
    /** Authoritative debt for a credit-card account: opening debt + OUT - IN. */
    CREDIT_DEBT(Set.of(SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)),
    /** Recorded current limit only; FINTRACK does not keep historical limit revisions. */
    CREDIT_LIMIT(Set.of(SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)),
    CREDIT_AVAILABLE(Set.of(SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)),
    CREDIT_UTILIZATION_PERCENT(Set.of(SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT)),
    TRANSFER_ID(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_AMOUNT(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_DATE(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_DAY(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_WEEK(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_MONTH(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    TRANSFER_YEAR(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    SOURCE_ACCOUNT_ID(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    SOURCE_ACCOUNT_INSTITUTION(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    SOURCE_ACCOUNT_TYPE(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    DESTINATION_ACCOUNT_ID(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    DESTINATION_ACCOUNT_INSTITUTION(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT)),
    DESTINATION_ACCOUNT_TYPE(Set.of(SemanticDataset.INTERNAL_TRANSFER_EVENT));

    private final Set<SemanticDataset> datasets;

    SemanticField(Set<SemanticDataset> datasets) {
        this.datasets = Set.copyOf(datasets);
    }

    public boolean belongsTo(SemanticDataset dataset) {
        return datasets.contains(dataset);
    }
}
