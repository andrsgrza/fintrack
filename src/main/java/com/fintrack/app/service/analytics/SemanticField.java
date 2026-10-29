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
    ACCOUNT_TYPE(EnumSet.of(SemanticDataset.TRANSACTION_LEDGER, SemanticDataset.ACCOUNT_POSITION_SNAPSHOT)),
    ACCOUNT_ID(EnumSet.of(SemanticDataset.TRANSACTION_LEDGER, SemanticDataset.ACCOUNT_POSITION_SNAPSHOT)),
    OBSERVATION_DATE(Set.of(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT)),
    OBSERVATION_MONTH(Set.of(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT)),
    POSITION_BALANCE(Set.of(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT));

    private final Set<SemanticDataset> datasets;

    SemanticField(Set<SemanticDataset> datasets) {
        this.datasets = Set.copyOf(datasets);
    }

    public boolean belongsTo(SemanticDataset dataset) {
        return datasets.contains(dataset);
    }
}
