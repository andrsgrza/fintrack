package com.fintrack.app.service.transfer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.repository.InternalTransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalTransferInvariantServiceTest {

    private InternalTransferInvariantService invariantService;
    private InternalTransferRepository internalTransferRepository;
    private FinancialTransaction outgoing;
    private FinancialTransaction incoming;

    @BeforeEach
    void setUp() {
        internalTransferRepository = mock(InternalTransferRepository.class);
        invariantService = new InternalTransferInvariantService(internalTransferRepository);
        User owner = new User();
        owner.setId(1L);

        FinancialAccount source = account(10L, owner, CurrencyCode.MXN, true);
        FinancialAccount destination = account(11L, owner, CurrencyCode.MXN, true);
        outgoing = transaction(20L, source, TransactionFlow.OUT, new BigDecimal("25.00"), LocalDate.of(2026, 10, 17));
        incoming = transaction(21L, destination, TransactionFlow.IN, new BigDecimal("25.00"), LocalDate.of(2026, 10, 17));
    }

    @Test
    void acceptsTheCanonicalOutAndInPair() {
        invariantService.validateExistingPair(outgoing, incoming);
    }

    @Test
    void rejectsDifferentTransactionDatesWithTheStableCode() {
        incoming.setTransactionDate(LocalDate.of(2026, 10, 18));

        assertCode(InternalTransferErrorCode.TRANSFER_DATE_MISMATCH, () -> invariantService.validateExistingPair(outgoing, incoming));
    }

    @Test
    void rejectsSameAccountWithTheStableCode() {
        incoming.setAccount(outgoing.getAccount());

        assertCode(InternalTransferErrorCode.TRANSFER_SAME_ACCOUNT, () -> invariantService.validateExistingPair(outgoing, incoming));
    }

    @Test
    void rejectsCurrencyAndAmountMismatches() {
        incoming.getAccount().setCurrency(CurrencyCode.USD);
        assertCode(InternalTransferErrorCode.TRANSFER_CURRENCY_MISMATCH, () -> invariantService.validateExistingPair(outgoing, incoming));

        incoming.getAccount().setCurrency(CurrencyCode.MXN);
        incoming.setAmount(new BigDecimal("24.99"));
        assertCode(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH, () -> invariantService.validateExistingPair(outgoing, incoming));
    }

    @Test
    void rejectsCrossOwnerAndWrongFlowsWithStableCodes() {
        User anotherOwner = new User();
        anotherOwner.setId(2L);
        incoming.getAccount().setUser(anotherOwner);
        assertCode(InternalTransferErrorCode.TRANSFER_CROSS_OWNER, () -> invariantService.validateExistingPair(outgoing, incoming));

        incoming.getAccount().setUser(outgoing.getAccount().getUser());
        incoming.setFlow(TransactionFlow.OUT);
        assertCode(InternalTransferErrorCode.TRANSFER_FLOW_MISMATCH, () -> invariantService.validateExistingPair(outgoing, incoming));
    }

    @Test
    void rejectsInvalidAmountsAndAnyRoleMembership() {
        assertCode(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH, () -> invariantService.normalizeAmount(new BigDecimal("0.00")));
        assertCode(InternalTransferErrorCode.TRANSFER_AMOUNT_MISMATCH, () -> invariantService.normalizeAmount(new BigDecimal("1.001")));

        when(internalTransferRepository.existsByTransactionIdInEitherRole(incoming.getId())).thenReturn(true);
        assertCode(InternalTransferErrorCode.TRANSFER_LEG_ALREADY_LINKED, () -> invariantService.requireUnlinked(outgoing, incoming));
    }

    @Test
    void permitsHistoricalInactiveLegsButRequiresAnActiveNewTarget() {
        outgoing.getAccount().setActive(false);
        invariantService.validateExistingPair(outgoing, incoming);

        incoming.getAccount().setActive(false);
        assertCode(InternalTransferErrorCode.TRANSFER_ACCOUNT_INACTIVE, () ->
            invariantService.validateExistingAccountAndNewCounterpart(outgoing.getAccount(), incoming.getAccount())
        );
    }

    private void assertCode(InternalTransferErrorCode expected, Runnable invocation) {
        assertThatThrownBy(invocation::run).isInstanceOf(InternalTransferCommandException.class).extracting("code").isEqualTo(expected);
    }

    private FinancialAccount account(Long id, User owner, CurrencyCode currency, boolean active) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setUser(owner);
        account.setCurrency(currency);
        account.setActive(active);
        return account;
    }

    private FinancialTransaction transaction(
        Long id,
        FinancialAccount account,
        TransactionFlow flow,
        BigDecimal amount,
        LocalDate transactionDate
    ) {
        FinancialTransaction transaction = new FinancialTransaction();
        transaction.setId(id);
        transaction.setAccount(account);
        transaction.setFlow(flow);
        transaction.setAmount(amount);
        transaction.setTransactionDate(transactionDate);
        return transaction;
    }
}
