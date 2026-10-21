package com.fintrack.app.service.transfer;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.service.dto.InternalTransferCounterpartDTO;
import com.fintrack.app.service.dto.InternalTransferLegDTO;
import com.fintrack.app.service.dto.InternalTransferReadDTO;
import java.util.Comparator;

/** Keeps command/query payloads product-shaped without leaking the generated entity DTO graph. */
public final class InternalTransferReadModelMapper {

    private InternalTransferReadModelMapper() {}

    public static InternalTransferReadDTO toRead(InternalTransfer transfer) {
        return toRead(transfer, true);
    }

    /**
     * Maps the bounded list projection without traversing lazy category/tag
     * collections.  Detail reads deliberately use {@link #toRead} instead.
     */
    public static InternalTransferReadDTO toSummary(InternalTransfer transfer) {
        return toRead(transfer, false);
    }

    private static InternalTransferReadDTO toRead(InternalTransfer transfer, boolean includeClassification) {
        InternalTransferReadDTO dto = new InternalTransferReadDTO();
        FinancialTransaction source = transfer.getOutgoingTransaction();
        FinancialTransaction destination = transfer.getIncomingTransaction();
        dto.setTransferId(transfer.getId());
        dto.setVersion(transfer.getVersion());
        dto.setCreatedAt(transfer.getCreatedAt());
        dto.setNotes(transfer.getNotes());
        dto.setTransactionDate(source.getTransactionDate());
        dto.setAmount(source.getAmount());
        dto.setCurrency(source.getAccount().getCurrency());
        dto.setSource(toLeg(source, includeClassification));
        dto.setDestination(toLeg(destination, includeClassification));
        return dto;
    }

    public static InternalTransferCounterpartDTO toCounterpart(FinancialTransaction transaction) {
        InternalTransferCounterpartDTO dto = new InternalTransferCounterpartDTO();
        dto.setTransactionId(transaction.getId());
        dto.setAccountId(transaction.getAccount().getId());
        dto.setAccountName(transaction.getAccount().getName());
        dto.setTransactionDate(transaction.getTransactionDate());
        dto.setPostingDate(transaction.getPostingDate());
        dto.setDescription(transaction.getDescription());
        dto.setExternalReference(transaction.getExternalReference());
        dto.setNotes(transaction.getNotes());
        dto.setAmount(transaction.getAmount());
        dto.setFlow(transaction.getFlow());
        dto.setCurrency(transaction.getAccount().getCurrency());
        dto.setOrigin(transaction.getOrigin());
        dto.setCategoryName(transaction.getCategory() == null ? null : transaction.getCategory().getName());
        dto.setTagNames(tagNames(transaction));
        return dto;
    }

    private static InternalTransferLegDTO toLeg(FinancialTransaction transaction, boolean includeClassification) {
        InternalTransferLegDTO dto = new InternalTransferLegDTO();
        dto.setTransactionId(transaction.getId());
        dto.setAccountId(transaction.getAccount().getId());
        dto.setAccountName(transaction.getAccount().getName());
        dto.setAccountType(transaction.getAccount().getAccountType());
        dto.setCurrency(transaction.getAccount().getCurrency());
        dto.setAccountActive(transaction.getAccount().getActive());
        dto.setTransactionDate(transaction.getTransactionDate());
        dto.setPostingDate(transaction.getPostingDate());
        dto.setDescription(transaction.getDescription());
        dto.setExternalReference(transaction.getExternalReference());
        dto.setNotes(transaction.getNotes());
        dto.setAmount(transaction.getAmount());
        dto.setFlow(transaction.getFlow());
        dto.setOrigin(transaction.getOrigin());
        if (includeClassification) {
            dto.setCategoryName(transaction.getCategory() == null ? null : transaction.getCategory().getName());
            dto.setTagNames(tagNames(transaction));
        }
        return dto;
    }

    private static java.util.List<String> tagNames(FinancialTransaction transaction) {
        return transaction
            .getTags()
            .stream()
            .map(Tag::getName)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.naturalOrder())
            .toList();
    }
}
