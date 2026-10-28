package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialAnalyticsDslServiceTest {

    private FinancialAnalyticsDslService service;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.getCurrentUserLogin()).thenReturn("user");
        service = new FinancialAnalyticsDslService(
            new FinancialAnalyticsValidator(
                new FinancialSemanticCatalog(),
                new FinancialMetricTimePolicy(Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)),
                currentUser,
                mock(FinancialAccountRepository.class),
                mock(CategoryRepository.class),
                mock(TagRepository.class)
            )
        );
    }

    @Test
    void parsesEveryClauseIntoTheCanonicalDefinitionAndFormatsItDeterministically() {
        FinancialAnalyticsDslParseResult result = service.parse(
            """
            select income_total, expense_total, net_economic_flow
            by month
            where account_type in (debit, cash) and flow = in
            using transaction_date
            order by month asc
            limit 100
            """
        );

        assertThat(result.definition()).isEqualTo(
            new QueryDefinition(
                AnalyticsSchema.VERSION,
                AnalyticsSchema.SEMANTIC_MODEL,
                List.of("INCOME_TOTAL", "EXPENSE_TOTAL", "NET_ECONOMIC_FLOW"),
                List.of("MONTH"),
                List.of(
                    new AnalyticalPredicate("ACCOUNT_TYPE", "IN", List.of("CASH", "DEBIT")),
                    new AnalyticalPredicate("FLOW", "EQUALS", List.of("IN"))
                ),
                "TRANSACTION_DATE",
                new AnalyticsSort("MONTH", "ASC"),
                100
            )
        );
        assertThat(result.canonicalText()).isEqualTo(
            "SELECT INCOME_TOTAL, EXPENSE_TOTAL, NET_ECONOMIC_FLOW BY MONTH WHERE ACCOUNT_TYPE IN (CASH, DEBIT) AND FLOW EQUALS IN USING TRANSACTION_DATE ORDER BY MONTH ASC LIMIT 100"
        );
        assertThat(service.parse(result.canonicalText())).isEqualTo(result);
    }

    @Test
    void normalizesDefaultsAndPreservesDatePredicateAndMeasureSemanticsThroughRoundTrips() {
        FinancialAnalyticsDslParseResult parsed = service.parse(
            "SELECT EXPENSE_TOTAL BY CATEGORY, MONTH WHERE TRANSACTION_DATE BETWEEN 2026-01-01 AND 2026-01-31"
        );

        assertThat(parsed.definition().dateBasis()).isEqualTo("TRANSACTION_DATE");
        assertThat(parsed.definition().limit()).isEqualTo(AnalyticsSchema.DEFAULT_LIMIT);
        assertThat(parsed.definition().sort()).isEqualTo(new AnalyticsSort("CATEGORY", "ASC"));
        assertThat(parsed.canonicalText()).isEqualTo(
            "SELECT EXPENSE_TOTAL BY CATEGORY, MONTH WHERE TRANSACTION_DATE BETWEEN 2026-01-01 AND 2026-01-31 USING TRANSACTION_DATE ORDER BY CATEGORY ASC LIMIT 100"
        );
        assertThat(service.format(parsed.definition())).isEqualTo(parsed.canonicalText());
        assertThat(service.parse(service.format(parsed.definition())).definition()).isEqualTo(parsed.definition());
    }

    @Test
    void reportsSyntaxFailuresWithStableCodesAndPreciseSourceCoordinates() {
        assertDslError("SELECT EXPENSE_TOTAL LIMIT 0", AnalyticsDiagnosticCode.INVALID_LIMIT, 1, 28);
        assertDslError("SELECT EXPENSE_TOTAL BY MONTH BY YEAR", AnalyticsDiagnosticCode.DUPLICATE_CLAUSE, 1, 31);
        assertDslError("SELECT EXPENSE_TOTAL FROM financial_transaction", AnalyticsDiagnosticCode.TRAILING_GARBAGE, 1, 22);
        assertDslError("SELECT EXPENSE_TOTAL WHERE FLOW EQUALS 'unterminated", AnalyticsDiagnosticCode.INVALID_LITERAL, 1, 40);
        assertDslError("SELECT EXPENSE_TOTAL ORDER MONTH ASC", AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, 1, 28);
    }

    @Test
    void delegatesUnknownAndIncompatibleSemanticsToTheSharedCatalogValidator() {
        FinancialAnalyticsDslException unknown = dslFailure("SELECT EXPENSE_TOTAL, NOT_A_METRIC");
        FinancialAnalyticsDslException incompatible = dslFailure("SELECT INCOME_TOTAL, CREDIT_CARD_DEBT");

        assertThat(unknown.getDiagnostics()).extracting(AnalyticsDiagnostic::code).containsExactly(AnalyticsDiagnosticCode.UNKNOWN_METRIC);
        assertThat(unknown.getDiagnostics().get(0).line()).isEqualTo(1);
        assertThat(unknown.getDiagnostics().get(0).column()).isEqualTo(23);
        assertThat(incompatible.getDiagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_TIME_MODE, AnalyticsDiagnosticCode.INCOMPATIBLE_METRIC_EXECUTOR);
    }

    @Test
    void boundsInputAndRejectsInjectionAndUnsupportedOperatorsWithoutAnySqlFallback() {
        assertDslError("SELECT EXPENSE_TOTAL WHERE FLOW UNION SELECT", AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, 1, 33);
        assertDslError("SELECT EXPENSE_TOTAL WHERE FLOW LIKE IN", AnalyticsDiagnosticCode.UNEXPECTED_TOKEN, 1, 33);
        assertDslCode("SELECT " + "X".repeat(FinancialAnalyticsDslLexer.MAX_INPUT_LENGTH), AnalyticsDiagnosticCode.INPUT_TOO_LONG);
        assertDslCode(
            "SELECT " + String.join(", ", java.util.Collections.nCopies(FinancialAnalyticsDslLexer.MAX_TOKEN_COUNT, "EXPENSE_TOTAL")),
            AnalyticsDiagnosticCode.TOO_MANY_TOKENS
        );
    }

    @Test
    void lexerKeepsQuotedUnicodeAndEscapedQuotesAsLiteralContent() {
        List<FinancialAnalyticsDslToken> tokens = new FinancialAnalyticsDslLexer()
            .tokenize("SELECT EXPENSE_TOTAL WHERE FLOW EQUALS 'MÉXICO ''único''' ");

        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.type()).isEqualTo(FinancialAnalyticsDslToken.Type.STRING);
            assertThat(token.text()).isEqualTo("MÉXICO 'único'");
        });
    }

    private FinancialAnalyticsDslException dslFailure(String text) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(() -> service.parse(text), FinancialAnalyticsDslException.class);
    }

    private void assertDslError(String text, AnalyticsDiagnosticCode code, int line, int column) {
        FinancialAnalyticsDslException exception = dslFailure(text);
        assertThat(exception).isNotNull();
        assertThat(exception.getDiagnostics()).hasSize(1);
        AnalyticsDiagnostic diagnostic = exception.getDiagnostics().get(0);
        assertThat(diagnostic.code()).isEqualTo(code);
        assertThat(diagnostic.line()).isEqualTo(line);
        assertThat(diagnostic.column()).isEqualTo(column);
    }

    private void assertDslCode(String text, AnalyticsDiagnosticCode code) {
        FinancialAnalyticsDslException exception = dslFailure(text);
        assertThat(exception).isNotNull();
        assertThat(exception.getDiagnostics()).extracting(AnalyticsDiagnostic::code).containsExactly(code);
    }
}
