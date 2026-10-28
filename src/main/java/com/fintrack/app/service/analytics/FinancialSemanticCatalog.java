package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.enumeration.AccountType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The only semantic registry in the clean Analytics architecture. */
@Component
public class FinancialSemanticCatalog {

    public static final String TRANSACTION_DATE = "TRANSACTION_DATE";
    public static final String POSTING_DATE = "POSTING_DATE";
    public static final String DAY = "DAY";
    public static final String WEEK = "WEEK";
    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String ACCOUNT_TYPE = "ACCOUNT_TYPE";
    public static final String CURRENCY = "CURRENCY";
    public static final String CATEGORY = "CATEGORY";
    public static final String ROOT_CATEGORY = "ROOT_CATEGORY";
    public static final String TAG = "TAG";
    public static final String ORIGIN = "ORIGIN";
    public static final String FLOW = "FLOW";
    public static final String TRANSFER_STATUS = "TRANSFER_STATUS";
    public static final String EQUALS = "EQUALS";
    public static final String IN = "IN";
    public static final String BETWEEN = "BETWEEN";

    private static final Set<AccountType> ALL_ACCOUNT_TYPES = EnumSet.allOf(AccountType.class);
    private static final Set<AccountType> ASSET_ACCOUNT_TYPES = EnumSet.of(AccountType.DEBIT, AccountType.CASH, AccountType.INVESTMENT);
    private static final Set<AccountType> CREDIT_CARD_ONLY = EnumSet.of(AccountType.CREDIT_CARD);
    private static final Set<String> PERIOD_DATE_BASES = Set.of(TRANSACTION_DATE, POSTING_DATE);
    private static final Set<String> AS_OF_DATE_BASES = Set.of(TRANSACTION_DATE);
    private static final Set<String> ECONOMIC_GROUPINGS = Set.of(
        DAY,
        WEEK,
        MONTH,
        YEAR,
        ACCOUNT,
        ACCOUNT_TYPE,
        CURRENCY,
        CATEGORY,
        ROOT_CATEGORY,
        TAG,
        ORIGIN,
        FLOW
    );
    private static final Set<String> MOVEMENT_GROUPINGS = union(ECONOMIC_GROUPINGS, Set.of(TRANSFER_STATUS));
    private static final Set<String> POSITION_GROUPINGS = Set.of(DAY, WEEK, MONTH, YEAR, ACCOUNT, ACCOUNT_TYPE, CURRENCY);
    private static final Set<String> TRANSFER_GROUPINGS = Set.of(DAY, WEEK, MONTH, YEAR, ACCOUNT, ACCOUNT_TYPE, CURRENCY);
    private static final Set<String> TRANSACTION_PREDICATES = Set.of(
        TRANSACTION_DATE,
        POSTING_DATE,
        FLOW,
        ORIGIN,
        ACCOUNT,
        ACCOUNT_TYPE,
        CURRENCY,
        CATEGORY,
        ROOT_CATEGORY,
        TAG,
        TRANSFER_STATUS
    );
    private static final Set<String> POSITION_PREDICATES = Set.of(ACCOUNT, ACCOUNT_TYPE, CURRENCY);
    private static final Set<String> TRANSFER_PREDICATES = Set.of(ACCOUNT, ACCOUNT_TYPE, CURRENCY);

    private final Map<String, FinancialMetricDefinition> metrics;
    private final Map<String, FinancialSemanticAttributeDefinition> attributes;

    public FinancialSemanticCatalog() {
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(buildMetrics()));
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(buildAttributes()));
    }

    public Optional<FinancialMetricDefinition> metric(String id) {
        return Optional.ofNullable(metrics.get(id));
    }

    public Optional<FinancialSemanticAttributeDefinition> attribute(String id) {
        return Optional.ofNullable(attributes.get(id));
    }

    public FinancialAnalyticsCatalog catalog(FinancialMetricTimePolicy timePolicy) {
        return new FinancialAnalyticsCatalog(
            AnalyticsSchema.VERSION,
            AnalyticsSchema.SEMANTIC_MODEL,
            AnalyticsSchema.CATALOG_VERSION,
            List.copyOf(metrics.values()),
            List.copyOf(attributes.values()),
            List.of(EQUALS, IN, BETWEEN),
            timePolicy.supportedRelativePeriods(),
            AnalyticsSchema.DEFAULT_LIMIT,
            AnalyticsSchema.MAX_LIMIT,
            AnalyticsSchema.MAX_MEASURES,
            AnalyticsSchema.MAX_GROUPINGS,
            AnalyticsSchema.MAX_PREDICATES,
            List.of("ECONOMIC_FLOW", "ACCOUNT_MOVEMENT", "ACCOUNT_POSITION", "CREDIT_CARD", "INTERNAL_TRANSFER")
        );
    }

    private Map<String, FinancialMetricDefinition> buildMetrics() {
        Map<String, FinancialMetricDefinition> definitions = new LinkedHashMap<>();
        definitions.put(
            "INCOME_TOTAL",
            economicMoney(
                "INCOME_TOTAL",
                "Income",
                "Ingresos económicos",
                "Eligible incoming money, excluding linked internal-transfer legs.",
                "Entradas económicas elegibles, excluyendo piernas vinculadas de transferencias internas."
            )
        );
        definitions.put(
            "EXPENSE_TOTAL",
            economicMoney(
                "EXPENSE_TOTAL",
                "Expenses",
                "Gastos económicos",
                "Eligible outgoing money, excluding linked internal-transfer legs.",
                "Salidas económicas elegibles, excluyendo piernas vinculadas de transferencias internas."
            )
        );
        definitions.put(
            "NET_ECONOMIC_FLOW",
            economicMoney(
                "NET_ECONOMIC_FLOW",
                "Net economic flow",
                "Flujo económico neto",
                "Eligible income minus eligible expenses.",
                "Ingresos económicos elegibles menos gastos económicos elegibles."
            )
        );
        definitions.put(
            "ECONOMIC_TRANSACTION_COUNT",
            periodMetric(
                "ECONOMIC_TRANSACTION_COUNT",
                "Economic transactions",
                "Transacciones económicas",
                "Count of eligible ledger transactions that are not linked internal-transfer legs.",
                "Conteo de movimientos elegibles que no son piernas de transferencia interna.",
                FinancialMetricUnit.COUNT,
                FinancialMetricCurrencyBehavior.NOT_MONETARY,
                FinancialMetricAdditivity.ADDITIVE,
                InternalTransferTreatment.EXCLUDE_LINKED_LEGS,
                EnumSet.of(HistoricalAvailabilityConstraint.ZERO_FOR_EMPTY_PERIOD)
            )
        );
        definitions.put(
            "AVERAGE_EXPENSE",
            periodMetric(
                "AVERAGE_EXPENSE",
                "Average expense",
                "Gasto promedio",
                "Eligible expenses divided by eligible outgoing transaction count.",
                "Gastos elegibles divididos entre el conteo de movimientos de salida elegibles.",
                FinancialMetricUnit.MONEY,
                FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY,
                FinancialMetricAdditivity.NON_ADDITIVE,
                InternalTransferTreatment.EXCLUDE_LINKED_LEGS,
                EnumSet.of(HistoricalAvailabilityConstraint.UNAVAILABLE_FOR_ZERO_DENOMINATOR)
            )
        );
        definitions.put(
            "ACCOUNT_INFLOW",
            movementMetric(
                "ACCOUNT_INFLOW",
                "Account inflow",
                "Entradas de cuenta",
                "All inflows for the selected accounts, including transfer legs.",
                "Todas las entradas de las cuentas seleccionadas, incluidas piernas de transferencia."
            )
        );
        definitions.put(
            "ACCOUNT_OUTFLOW",
            movementMetric(
                "ACCOUNT_OUTFLOW",
                "Account outflow",
                "Salidas de cuenta",
                "All outflows for the selected accounts, including transfer legs.",
                "Todas las salidas de las cuentas seleccionadas, incluidas piernas de transferencia."
            )
        );
        definitions.put(
            "ACCOUNT_NET_MOVEMENT",
            movementMetric(
                "ACCOUNT_NET_MOVEMENT",
                "Net account movement",
                "Movimiento neto de cuenta",
                "Account inflows minus account outflows.",
                "Entradas de cuenta menos salidas de cuenta."
            )
        );
        definitions.put(
            "ACCOUNT_BALANCE",
            positionMetric(
                "ACCOUNT_BALANCE",
                "Account balance",
                "Saldo registrado",
                "Recorded balance for debit, cash and investment accounts as of the selected date.",
                "Saldo registrado de cuentas de débito, efectivo e inversión a la fecha seleccionada.",
                ASSET_ACCOUNT_TYPES,
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.POINT_IN_TIME,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE)
            )
        );
        definitions.put(
            "ACCOUNT_SIGNED_POSITION",
            positionMetric(
                "ACCOUNT_SIGNED_POSITION",
                "Signed account position",
                "Posición registrada",
                "Asset balances are positive; credit-card debt is negative.",
                "Los saldos de activos son positivos; la deuda de tarjeta es negativa.",
                ALL_ACCOUNT_TYPES,
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.POINT_IN_TIME,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE)
            )
        );
        definitions.put(
            "NET_RECORDED_POSITION",
            positionMetric(
                "NET_RECORDED_POSITION",
                "Net recorded position",
                "Posición financiera registrada",
                "Sum of signed account positions within each currency partition.",
                "Suma de posiciones registradas con signo dentro de cada partición de moneda.",
                ALL_ACCOUNT_TYPES,
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.POINT_IN_TIME,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE)
            )
        );
        definitions.put(
            "CREDIT_CARD_DEBT",
            creditMetric(
                "CREDIT_CARD_DEBT",
                "Credit card debt",
                "Deuda de tarjeta",
                "Recorded credit-card debt as of the selected date; it does not require a credit limit.",
                "Deuda registrada de tarjeta a la fecha seleccionada; no requiere límite de crédito.",
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.POINT_IN_TIME,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE)
            )
        );
        definitions.put(
            "CREDIT_LIMIT",
            creditMetric(
                "CREDIT_LIMIT",
                "Credit limit",
                "Límite de crédito",
                "Current persisted credit limit; historical limits are unavailable without limit history.",
                "Límite de crédito actual persistido; límites históricos no están disponibles sin historial.",
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.NON_ADDITIVE,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT)
            )
        );
        definitions.put(
            "AVAILABLE_CREDIT",
            creditMetric(
                "AVAILABLE_CREDIT",
                "Available credit",
                "Crédito disponible",
                "Current credit limit minus recorded debt; no historical limit is invented.",
                "Límite actual menos deuda registrada; no se inventa un límite histórico.",
                FinancialMetricUnit.MONEY,
                FinancialMetricAdditivity.NON_ADDITIVE,
                EnumSet.of(HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT)
            )
        );
        definitions.put(
            "CREDIT_UTILIZATION",
            creditMetric(
                "CREDIT_UTILIZATION",
                "Credit utilization",
                "Utilización de crédito",
                "Recorded debt divided by a nonzero current credit limit.",
                "Deuda registrada dividida entre un límite actual distinto de cero.",
                FinancialMetricUnit.RATIO,
                FinancialMetricAdditivity.NON_ADDITIVE,
                EnumSet.of(
                    HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT,
                    HistoricalAvailabilityConstraint.REQUIRES_NONZERO_CREDIT_LIMIT
                )
            )
        );
        definitions.put(
            "INTERNAL_TRANSFER_COUNT",
            new FinancialMetricDefinition(
                "INTERNAL_TRANSFER_COUNT",
                "Internal transfers",
                "Transferencias internas",
                "Count of persisted internal-transfer relationships, never both transaction legs.",
                "Conteo de relaciones de transferencia interna persistidas, nunca ambas piernas.",
                "INTERNAL_TRANSFER",
                FinancialMetricTimeMode.PERIOD,
                FinancialMetricExecutorSpecialization.TRANSFER_RELATIONSHIP_AGGREGATE,
                new FinancialMetricApplicability(ALL_ACCOUNT_TYPES),
                Set.of(TRANSACTION_DATE),
                TRANSFER_GROUPINGS,
                TRANSFER_PREDICATES,
                FinancialMetricUnit.COUNT,
                FinancialMetricCurrencyBehavior.NOT_MONETARY,
                FinancialMetricAdditivity.ADDITIVE,
                InternalTransferTreatment.COUNT_RELATIONSHIPS,
                EnumSet.of(HistoricalAvailabilityConstraint.ZERO_FOR_EMPTY_PERIOD),
                List.of("A transfer is one relationship even though it has two transaction legs.")
            )
        );
        return definitions;
    }

    private FinancialMetricDefinition economicMoney(String id, String en, String es, String descriptionEn, String descriptionEs) {
        return periodMetric(
            id,
            en,
            es,
            descriptionEn,
            descriptionEs,
            FinancialMetricUnit.MONEY,
            FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY,
            FinancialMetricAdditivity.ADDITIVE,
            InternalTransferTreatment.EXCLUDE_LINKED_LEGS,
            EnumSet.of(HistoricalAvailabilityConstraint.ZERO_FOR_EMPTY_PERIOD)
        );
    }

    private FinancialMetricDefinition periodMetric(
        String id,
        String en,
        String es,
        String descriptionEn,
        String descriptionEs,
        FinancialMetricUnit unit,
        FinancialMetricCurrencyBehavior currencyBehavior,
        FinancialMetricAdditivity additivity,
        InternalTransferTreatment transferTreatment,
        Set<HistoricalAvailabilityConstraint> availability
    ) {
        return new FinancialMetricDefinition(
            id,
            en,
            es,
            descriptionEn,
            descriptionEs,
            "ECONOMIC_FLOW",
            FinancialMetricTimeMode.PERIOD,
            FinancialMetricExecutorSpecialization.TRANSACTION_AGGREGATE,
            new FinancialMetricApplicability(ALL_ACCOUNT_TYPES),
            PERIOD_DATE_BASES,
            ECONOMIC_GROUPINGS,
            TRANSACTION_PREDICATES,
            unit,
            currencyBehavior,
            additivity,
            transferTreatment,
            availability,
            warnings(availability, transferTreatment)
        );
    }

    private FinancialMetricDefinition movementMetric(String id, String en, String es, String descriptionEn, String descriptionEs) {
        return new FinancialMetricDefinition(
            id,
            en,
            es,
            descriptionEn,
            descriptionEs,
            "ACCOUNT_MOVEMENT",
            FinancialMetricTimeMode.PERIOD,
            FinancialMetricExecutorSpecialization.TRANSACTION_AGGREGATE,
            new FinancialMetricApplicability(ALL_ACCOUNT_TYPES),
            PERIOD_DATE_BASES,
            MOVEMENT_GROUPINGS,
            TRANSACTION_PREDICATES,
            FinancialMetricUnit.MONEY,
            FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY,
            FinancialMetricAdditivity.ADDITIVE,
            InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS,
            EnumSet.of(HistoricalAvailabilityConstraint.ZERO_FOR_EMPTY_PERIOD),
            List.of("Linked internal-transfer legs remain relevant to an account's movement.")
        );
    }

    private FinancialMetricDefinition positionMetric(
        String id,
        String en,
        String es,
        String descriptionEn,
        String descriptionEs,
        Set<AccountType> accountTypes,
        FinancialMetricUnit unit,
        FinancialMetricAdditivity additivity,
        Set<HistoricalAvailabilityConstraint> availability
    ) {
        return new FinancialMetricDefinition(
            id,
            en,
            es,
            descriptionEn,
            descriptionEs,
            "ACCOUNT_POSITION",
            FinancialMetricTimeMode.AS_OF,
            FinancialMetricExecutorSpecialization.ACCOUNT_SNAPSHOT,
            new FinancialMetricApplicability(accountTypes),
            AS_OF_DATE_BASES,
            POSITION_GROUPINGS,
            POSITION_PREDICATES,
            unit,
            FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY,
            additivity,
            InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS,
            availability,
            warnings(availability, InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS)
        );
    }

    private FinancialMetricDefinition creditMetric(
        String id,
        String en,
        String es,
        String descriptionEn,
        String descriptionEs,
        FinancialMetricUnit unit,
        FinancialMetricAdditivity additivity,
        Set<HistoricalAvailabilityConstraint> availability
    ) {
        return new FinancialMetricDefinition(
            id,
            en,
            es,
            descriptionEn,
            descriptionEs,
            "CREDIT_CARD",
            FinancialMetricTimeMode.AS_OF,
            FinancialMetricExecutorSpecialization.ACCOUNT_SNAPSHOT,
            new FinancialMetricApplicability(CREDIT_CARD_ONLY),
            AS_OF_DATE_BASES,
            POSITION_GROUPINGS,
            POSITION_PREDICATES,
            unit,
            unit == FinancialMetricUnit.RATIO
                ? FinancialMetricCurrencyBehavior.NOT_MONETARY
                : FinancialMetricCurrencyBehavior.PARTITION_BY_CURRENCY,
            additivity,
            InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS,
            availability,
            warnings(availability, InternalTransferTreatment.INCLUDE_ACCOUNT_RELATIVE_LEGS)
        );
    }

    private List<String> warnings(Set<HistoricalAvailabilityConstraint> availability, InternalTransferTreatment transferTreatment) {
        List<String> warnings = new ArrayList<>();
        if (availability.contains(HistoricalAvailabilityConstraint.REQUIRES_ACCOUNT_OPENING_DATE)) {
            warnings.add("No account position is invented before an account's initial balance date.");
        }
        if (availability.contains(HistoricalAvailabilityConstraint.REQUIRES_CURRENT_CREDIT_LIMIT)) {
            warnings.add("Historical credit limits are unavailable because FINTRACK stores only the current limit.");
        }
        if (transferTreatment == InternalTransferTreatment.EXCLUDE_LINKED_LEGS) {
            warnings.add("Linked internal-transfer legs are excluded from economic measures.");
        }
        return List.copyOf(warnings);
    }

    private Map<String, FinancialSemanticAttributeDefinition> buildAttributes() {
        Map<String, FinancialSemanticAttributeDefinition> definitions = new LinkedHashMap<>();
        definitions.put(TRANSACTION_DATE, dateAttribute(TRANSACTION_DATE, "Transaction date", "Fecha de transacción"));
        definitions.put(POSTING_DATE, dateAttribute(POSTING_DATE, "Posting date", "Fecha de registro"));
        definitions.put(DAY, temporalGrouping(DAY, "Day", "Día"));
        definitions.put(WEEK, temporalGrouping(WEEK, "Week", "Semana"));
        definitions.put(MONTH, temporalGrouping(MONTH, "Month", "Mes"));
        definitions.put(YEAR, temporalGrouping(YEAR, "Year", "Año"));
        definitions.put(
            ACCOUNT,
            referenceAttribute(
                ACCOUNT,
                "Account",
                "Cuenta",
                Set.of("ACCOUNT_MOVEMENT", "ACCOUNT_POSITION", "CREDIT_CARD", "ECONOMIC_FLOW", "INTERNAL_TRANSFER")
            )
        );
        definitions.put(
            ACCOUNT_TYPE,
            enumAttribute(
                ACCOUNT_TYPE,
                "Account type",
                "Tipo de cuenta",
                Set.of("ACCOUNT_MOVEMENT", "ACCOUNT_POSITION", "CREDIT_CARD", "ECONOMIC_FLOW", "INTERNAL_TRANSFER")
            )
        );
        definitions.put(CURRENCY, currencyAttribute());
        definitions.put(CATEGORY, referenceAttribute(CATEGORY, "Category", "Categoría", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW")));
        definitions.put(
            ROOT_CATEGORY,
            referenceAttribute(ROOT_CATEGORY, "Root category", "Categoría raíz", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW"))
        );
        definitions.put(TAG, referenceAttribute(TAG, "Tag", "Etiqueta", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW")));
        definitions.put(ORIGIN, enumAttribute(ORIGIN, "Origin", "Origen", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW")));
        definitions.put(FLOW, enumAttribute(FLOW, "Flow", "Flujo", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW")));
        definitions.put(
            TRANSFER_STATUS,
            enumAttribute(TRANSFER_STATUS, "Transfer status", "Estado de transferencia", Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW"))
        );
        return definitions;
    }

    private FinancialSemanticAttributeDefinition dateAttribute(String id, String en, String es) {
        return new FinancialSemanticAttributeDefinition(
            id,
            en,
            es,
            "A governed financial date.",
            "Una fecha financiera gobernada.",
            SemanticValueType.DATE,
            Set.of(EQUALS, BETWEEN),
            true,
            true,
            Set.of("ACCOUNT_MOVEMENT", "ECONOMIC_FLOW", "INTERNAL_TRANSFER"),
            "LOW",
            false
        );
    }

    private FinancialSemanticAttributeDefinition temporalGrouping(String id, String en, String es) {
        return new FinancialSemanticAttributeDefinition(
            id,
            en,
            es,
            "A time bucket derived by the server.",
            "Un bloque temporal derivado por el servidor.",
            SemanticValueType.TEMPORAL_BUCKET,
            Set.of(),
            true,
            false,
            Set.of("ACCOUNT_MOVEMENT", "ACCOUNT_POSITION", "CREDIT_CARD", "ECONOMIC_FLOW", "INTERNAL_TRANSFER"),
            "LOW",
            false
        );
    }

    private FinancialSemanticAttributeDefinition referenceAttribute(String id, String en, String es, Set<String> subjects) {
        return new FinancialSemanticAttributeDefinition(
            id,
            en,
            es,
            "An owned financial reference.",
            "Una referencia financiera propiedad del usuario.",
            SemanticValueType.IDENTIFIER,
            Set.of(EQUALS, IN),
            true,
            true,
            subjects,
            "HIGH",
            true
        );
    }

    private FinancialSemanticAttributeDefinition enumAttribute(String id, String en, String es, Set<String> subjects) {
        return new FinancialSemanticAttributeDefinition(
            id,
            en,
            es,
            "A controlled FINTRACK classification.",
            "Una clasificación controlada de FINTRACK.",
            SemanticValueType.ENUM,
            Set.of(EQUALS, IN),
            true,
            true,
            subjects,
            "LOW",
            false
        );
    }

    private FinancialSemanticAttributeDefinition currencyAttribute() {
        return new FinancialSemanticAttributeDefinition(
            CURRENCY,
            "Currency",
            "Moneda",
            "The account currency without implicit conversion.",
            "La moneda de la cuenta sin conversión implícita.",
            SemanticValueType.CURRENCY_CODE,
            Set.of(EQUALS, IN),
            true,
            true,
            Set.of("ACCOUNT_MOVEMENT", "ACCOUNT_POSITION", "CREDIT_CARD", "ECONOMIC_FLOW", "INTERNAL_TRANSFER"),
            "LOW",
            false
        );
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
