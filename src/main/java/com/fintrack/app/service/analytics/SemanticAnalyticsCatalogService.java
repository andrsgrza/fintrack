package com.fintrack.app.service.analytics;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The canonical registry for the executable semantic surface. Validation and capability disclosure consult this
 * same registry so the API never advertises expressions that the backend does not execute.
 */
@Component
public class SemanticAnalyticsCatalogService {

    private static final List<SemanticPredicateOperator> EQUALS_OR_IN = List.of(
        SemanticPredicateOperator.EQUALS,
        SemanticPredicateOperator.IN
    );

    private static final List<SemanticPredicateOperator> CATEGORY_OPERATORS = List.of(
        SemanticPredicateOperator.EQUALS,
        SemanticPredicateOperator.IN,
        SemanticPredicateOperator.DESCENDANT_OF,
        SemanticPredicateOperator.IS_UNCATEGORIZED
    );

    private static final List<SemanticPredicateOperator> TAG_OPERATORS = List.of(
        SemanticPredicateOperator.HAS_ANY,
        SemanticPredicateOperator.HAS_ALL,
        SemanticPredicateOperator.HAS_NONE
    );

    private final Map<SemanticDataset, SemanticDatasetDefinition> datasets;

    public SemanticAnalyticsCatalogService() {
        Map<SemanticDataset, SemanticDatasetDefinition> definitions = new EnumMap<>(SemanticDataset.class);
        definitions.put(SemanticDataset.TRANSACTION_LEDGER, transactionLedger());
        definitions.put(SemanticDataset.FINANCIAL_POSITION_SNAPSHOT, financialPositionSnapshot());
        definitions.put(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT, accountPositionSnapshot());
        definitions.put(SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT, creditAccountSnapshot());
        definitions.put(SemanticDataset.INTERNAL_TRANSFER_EVENT, internalTransferEvent());
        datasets = Map.copyOf(definitions);
    }

    public SemanticAnalyticsCatalog catalog(SemanticAnalyticsTimePolicy timePolicy) {
        return new SemanticAnalyticsCatalog(
            SemanticAnalyticsSchema.VERSION,
            SemanticAnalyticsSchema.SEMANTIC_MODEL,
            SemanticAnalyticsSchema.CATALOG_VERSION,
            Arrays.stream(SemanticDataset.values()).map(datasets::get).toList(),
            timePolicy.supportedRelativePeriods(),
            SemanticAnalyticsSchema.DEFAULT_LIMIT,
            SemanticAnalyticsSchema.MAX_LIMIT,
            true
        );
    }

    public Optional<SemanticDatasetDefinition> dataset(SemanticDataset dataset) {
        return Optional.ofNullable(datasets.get(dataset));
    }

    public SemanticField timeBinding(SemanticDataset dataset) {
        return dataset(dataset).map(SemanticDatasetDefinition::timeBinding).orElse(null);
    }

    public boolean supportsAggregate(SemanticDataset dataset, SemanticAggregateFunction function, SemanticField field) {
        return dataset(dataset)
            .map(SemanticDatasetDefinition::aggregateCapabilities)
            .stream()
            .flatMap(List::stream)
            .anyMatch(capability -> capability.function() == function && capability.field() == field);
    }

    public boolean supportsGrouping(SemanticDataset dataset, SemanticField field) {
        return dataset(dataset).map(SemanticDatasetDefinition::groupings).map(groups -> groups.contains(field)).orElse(false);
    }

    public boolean supportsPredicate(SemanticDataset dataset, SemanticField field, SemanticPredicateOperator operator) {
        return dataset(dataset)
            .map(SemanticDatasetDefinition::predicateCapabilities)
            .stream()
            .flatMap(List::stream)
            .anyMatch(capability -> capability.field() == field && capability.operators().contains(operator));
    }

    private SemanticDatasetDefinition transactionLedger() {
        return new SemanticDatasetDefinition(
            SemanticDataset.TRANSACTION_LEDGER,
            "one FinancialTransaction",
            SemanticField.TRANSACTION_DATE,
            List.of(
                new SemanticRelationshipDefinition("ACCOUNT", "FinancialAccount", "MANY_TO_ONE", "Account institution, type and currency"),
                new SemanticRelationshipDefinition("CATEGORY", "Category", "MANY_TO_ONE", "Owner-scoped assigned category and hierarchy"),
                new SemanticRelationshipDefinition("TAG", "Tag", "MANY_TO_MANY", "Owner-scoped tags; grouping overlaps by design")
            ),
            List.of(
                field(SemanticField.TRANSACTION_ID, SemanticValueType.IDENTIFIER, null, true, false, false),
                field(SemanticField.TRANSACTION_AMOUNT, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false),
                field(SemanticField.TRANSACTION_FLOW, SemanticValueType.ENUM, null, false, true, false),
                field(SemanticField.TRANSACTION_DATE, SemanticValueType.DATE, null, false, false, false),
                field(SemanticField.TRANSACTION_DAY, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSACTION_WEEK, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSACTION_MONTH, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSACTION_YEAR, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSFER_LINK_STATUS, SemanticValueType.ENUM, null, false, true, false),
                field(SemanticField.ACCOUNT_INSTITUTION, SemanticValueType.TEXT, null, false, false, true),
                field(SemanticField.ACCOUNT_ID, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.CATEGORY_ID, SemanticValueType.IDENTIFIER, null, false, true, true),
                field(SemanticField.CATEGORY_ROOT, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.CATEGORY_SUBCATEGORY, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.CATEGORY_BRANCH_CHILD, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.TAG_ID, SemanticValueType.IDENTIFIER, null, false, true, true),
                field(SemanticField.ACCOUNT_TYPE, SemanticValueType.ENUM, null, false, true, true)
            ),
            List.of(
                aggregate(
                    SemanticAggregateFunction.COUNT,
                    SemanticField.TRANSACTION_ID,
                    SemanticUnit.COUNT,
                    SemanticAdditivityPolicy.ADDITIVE
                ),
                aggregate(
                    SemanticAggregateFunction.AVG,
                    SemanticField.TRANSACTION_AMOUNT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.NON_ADDITIVE
                ),
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSACTION_AMOUNT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.ADDITIVE
                ),
                aggregate(
                    SemanticAggregateFunction.NET,
                    SemanticField.TRANSACTION_AMOUNT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.ADDITIVE
                )
            ),
            List.of(
                predicate(SemanticField.TRANSACTION_FLOW),
                predicate(SemanticField.TRANSFER_LINK_STATUS),
                predicate(SemanticField.ACCOUNT_TYPE),
                predicate(SemanticField.CATEGORY_ID, CATEGORY_OPERATORS),
                predicate(SemanticField.TAG_ID, TAG_OPERATORS)
            ),
            List.of(
                SemanticField.TRANSACTION_DAY,
                SemanticField.TRANSACTION_WEEK,
                SemanticField.TRANSACTION_MONTH,
                SemanticField.TRANSACTION_YEAR,
                SemanticField.ACCOUNT_INSTITUTION,
                SemanticField.ACCOUNT_ID,
                SemanticField.ACCOUNT_TYPE,
                SemanticField.CATEGORY_ID,
                SemanticField.CATEGORY_ROOT,
                SemanticField.CATEGORY_SUBCATEGORY,
                SemanticField.CATEGORY_BRANCH_CHILD,
                SemanticField.TAG_ID
            ),
            SemanticAdditivityPolicy.ADDITIVE,
            SemanticCurrencyPolicy.PARTITION_BY_ACCOUNT_CURRENCY,
            "Transaction date selects an inclusive period. Monetary results are partitioned by account currency."
        );
    }

    private SemanticDatasetDefinition financialPositionSnapshot() {
        return new SemanticDatasetDefinition(
            SemanticDataset.FINANCIAL_POSITION_SNAPSHOT,
            "compatible asset balances less recorded credit-card debt at one authoritative observation cutoff, partitioned by currency",
            SemanticField.OBSERVATION_DATE,
            List.of(new SemanticRelationshipDefinition("ACCOUNT", "FinancialAccount", "MANY_TO_ONE", "Owned account, type and currency")),
            List.of(
                field(SemanticField.OBSERVATION_DATE, SemanticValueType.DATE, null, false, false, false),
                field(SemanticField.FINANCIAL_POSITION, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false)
            ),
            List.of(
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.FINANCIAL_POSITION,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH
                )
            ),
            List.of(),
            List.of(),
            SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH,
            SemanticCurrencyPolicy.PARTITION_BY_ACCOUNT_CURRENCY,
            "Uses one authoritative cutoff: compatible asset balances less recorded credit-card debt. Currencies are never combined, and accounts without history at the cutoff make that currency partial or unavailable."
        );
    }

    private SemanticDatasetDefinition accountPositionSnapshot() {
        return new SemanticDatasetDefinition(
            SemanticDataset.ACCOUNT_POSITION_SNAPSHOT,
            "one asset FinancialAccount at one authoritative observation date",
            SemanticField.OBSERVATION_DATE,
            List.of(
                new SemanticRelationshipDefinition("ACCOUNT", "FinancialAccount", "MANY_TO_ONE", "Account identity, type and currency")
            ),
            List.of(
                field(SemanticField.ACCOUNT_ID, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.ACCOUNT_TYPE, SemanticValueType.ENUM, null, false, true, false),
                field(SemanticField.OBSERVATION_DATE, SemanticValueType.DATE, null, false, false, false),
                field(SemanticField.OBSERVATION_MONTH, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.POSITION_BALANCE, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false)
            ),
            List.of(
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.POSITION_BALANCE,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH
                )
            ),
            List.of(predicate(SemanticField.ACCOUNT_TYPE)),
            List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH),
            SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH,
            SemanticCurrencyPolicy.PARTITION_BY_ACCOUNT_CURRENCY,
            "The value is calculated from initialBalance, initialBalanceDate and posted transaction flow. Dates before opening history are unavailable."
        );
    }

    private SemanticDatasetDefinition creditAccountSnapshot() {
        return new SemanticDatasetDefinition(
            SemanticDataset.CREDIT_ACCOUNT_SNAPSHOT,
            "one credit-card FinancialAccount at one authoritative observation date",
            SemanticField.OBSERVATION_DATE,
            List.of(
                new SemanticRelationshipDefinition(
                    "ACCOUNT",
                    "FinancialAccount",
                    "MANY_TO_ONE",
                    "Credit-card account identity, type and currency"
                ),
                new SemanticRelationshipDefinition(
                    "CREDIT_DETAILS",
                    "CreditAccountDetails",
                    "ONE_TO_ONE_OPTIONAL",
                    "Current recorded credit limit; its historical revisions are not retained"
                )
            ),
            List.of(
                field(SemanticField.ACCOUNT_ID, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.ACCOUNT_TYPE, SemanticValueType.ENUM, null, false, true, false),
                field(SemanticField.OBSERVATION_DATE, SemanticValueType.DATE, null, false, false, false),
                field(SemanticField.OBSERVATION_MONTH, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.CREDIT_DEBT, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false),
                field(SemanticField.CREDIT_LIMIT, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false),
                field(SemanticField.CREDIT_AVAILABLE, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false),
                field(SemanticField.CREDIT_UTILIZATION_PERCENT, SemanticValueType.DECIMAL, SemanticUnit.PERCENT, true, false, false)
            ),
            List.of(
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.CREDIT_DEBT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH
                ),
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.CREDIT_LIMIT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT
                ),
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.CREDIT_AVAILABLE,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT
                ),
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.CREDIT_UTILIZATION_PERCENT,
                    SemanticUnit.PERCENT,
                    SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT
                )
            ),
            List.of(predicate(SemanticField.ACCOUNT_TYPE)),
            List.of(SemanticField.ACCOUNT_ID, SemanticField.OBSERVATION_MONTH),
            SemanticAdditivityPolicy.POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH,
            SemanticCurrencyPolicy.PARTITION_BY_ACCOUNT_CURRENCY,
            "Credit debt is an authoritative point-in-time value. Limit, available credit and utilization are current-only because FINTRACK does not retain limit history."
        );
    }

    private SemanticDatasetDefinition internalTransferEvent() {
        return new SemanticDatasetDefinition(
            SemanticDataset.INTERNAL_TRANSFER_EVENT,
            "one validated InternalTransfer aggregate",
            SemanticField.TRANSFER_DATE,
            List.of(
                new SemanticRelationshipDefinition(
                    "SOURCE_ACCOUNT",
                    "FinancialAccount",
                    "MANY_TO_ONE",
                    "Outgoing transfer account, institution, type and currency"
                ),
                new SemanticRelationshipDefinition(
                    "DESTINATION_ACCOUNT",
                    "FinancialAccount",
                    "MANY_TO_ONE",
                    "Incoming transfer account, institution, type and currency"
                )
            ),
            List.of(
                field(SemanticField.TRANSFER_ID, SemanticValueType.IDENTIFIER, null, true, false, false),
                field(SemanticField.TRANSFER_AMOUNT, SemanticValueType.DECIMAL, SemanticUnit.MONEY, true, false, false),
                field(SemanticField.TRANSFER_DATE, SemanticValueType.DATE, null, false, false, false),
                field(SemanticField.TRANSFER_DAY, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSFER_WEEK, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSFER_MONTH, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.TRANSFER_YEAR, SemanticValueType.TEMPORAL_BUCKET, null, false, false, true),
                field(SemanticField.SOURCE_ACCOUNT_ID, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.SOURCE_ACCOUNT_INSTITUTION, SemanticValueType.TEXT, null, false, false, true),
                field(SemanticField.SOURCE_ACCOUNT_TYPE, SemanticValueType.ENUM, null, false, true, true),
                field(SemanticField.DESTINATION_ACCOUNT_ID, SemanticValueType.IDENTIFIER, null, false, false, true),
                field(SemanticField.DESTINATION_ACCOUNT_INSTITUTION, SemanticValueType.TEXT, null, false, false, true),
                field(SemanticField.DESTINATION_ACCOUNT_TYPE, SemanticValueType.ENUM, null, false, true, true)
            ),
            List.of(
                aggregate(
                    SemanticAggregateFunction.COUNT,
                    SemanticField.TRANSFER_ID,
                    SemanticUnit.COUNT,
                    SemanticAdditivityPolicy.ADDITIVE
                ),
                aggregate(
                    SemanticAggregateFunction.SUM,
                    SemanticField.TRANSFER_AMOUNT,
                    SemanticUnit.MONEY,
                    SemanticAdditivityPolicy.ADDITIVE
                )
            ),
            List.of(predicate(SemanticField.SOURCE_ACCOUNT_TYPE), predicate(SemanticField.DESTINATION_ACCOUNT_TYPE)),
            List.of(
                SemanticField.TRANSFER_DAY,
                SemanticField.TRANSFER_WEEK,
                SemanticField.TRANSFER_MONTH,
                SemanticField.TRANSFER_YEAR,
                SemanticField.SOURCE_ACCOUNT_ID,
                SemanticField.SOURCE_ACCOUNT_INSTITUTION,
                SemanticField.SOURCE_ACCOUNT_TYPE,
                SemanticField.DESTINATION_ACCOUNT_ID,
                SemanticField.DESTINATION_ACCOUNT_INSTITUTION,
                SemanticField.DESTINATION_ACCOUNT_TYPE
            ),
            SemanticAdditivityPolicy.ADDITIVE,
            SemanticCurrencyPolicy.PARTITION_BY_ACCOUNT_CURRENCY,
            "One InternalTransfer is counted once using its validated outgoing-leg date and amount. Account context includes the event when either owned endpoint is selected."
        );
    }

    private SemanticFieldDefinition field(
        SemanticField field,
        SemanticValueType valueType,
        SemanticUnit unit,
        boolean aggregateable,
        boolean filterable,
        boolean groupable
    ) {
        return new SemanticFieldDefinition(field, valueType, unit, aggregateable, filterable, groupable, label(field), description(field));
    }

    private SemanticAggregateCapability aggregate(
        SemanticAggregateFunction function,
        SemanticField field,
        SemanticUnit outputUnit,
        SemanticAdditivityPolicy additivity
    ) {
        return new SemanticAggregateCapability(function, field, outputUnit, additivity);
    }

    private SemanticPredicateCapability predicate(SemanticField field) {
        return new SemanticPredicateCapability(field, EQUALS_OR_IN);
    }

    private SemanticPredicateCapability predicate(SemanticField field, List<SemanticPredicateOperator> operators) {
        return new SemanticPredicateCapability(field, operators);
    }

    private SemanticLocalizedText label(SemanticField field) {
        return switch (field) {
            case TRANSACTION_ID -> text("Movimiento", "Transaction");
            case TRANSACTION_AMOUNT -> text("Importe", "Amount");
            case TRANSACTION_FLOW -> text("Flujo", "Flow");
            case TRANSACTION_DATE -> text("Fecha de transacción", "Transaction date");
            case TRANSACTION_DAY -> text("Día de transacción", "Transaction day");
            case TRANSACTION_WEEK -> text("Semana ISO de transacción", "Transaction ISO week");
            case TRANSACTION_MONTH -> text("Mes de transacción", "Transaction month");
            case TRANSACTION_YEAR -> text("Año de transacción", "Transaction year");
            case TRANSFER_LINK_STATUS -> text("Transferencia interna", "Internal transfer");
            case ACCOUNT_INSTITUTION -> text("Institución financiera", "Financial institution");
            case CATEGORY_ID -> text("Categoría", "Category");
            case CATEGORY_ROOT -> text("Categoría principal", "Root category");
            case CATEGORY_SUBCATEGORY -> text("Subcategoría principal", "First-level subcategory");
            case CATEGORY_BRANCH_CHILD -> text("Rama de categoría", "Category branch");
            case TAG_ID -> text("Etiqueta", "Tag");
            case ACCOUNT_TYPE -> text("Tipo de cuenta", "Account type");
            case ACCOUNT_ID -> text("Cuenta", "Account");
            case OBSERVATION_DATE -> text("Fecha de observación", "Observation date");
            case OBSERVATION_MONTH -> text("Mes de observación", "Observation month");
            case POSITION_BALANCE -> text("Saldo registrado", "Recorded balance");
            case FINANCIAL_POSITION -> text("Posición financiera registrada", "Registered financial position");
            case CREDIT_DEBT -> text("Deuda de tarjeta", "Credit-card debt");
            case CREDIT_LIMIT -> text("Límite de crédito actual", "Current credit limit");
            case CREDIT_AVAILABLE -> text("Crédito disponible actual", "Current available credit");
            case CREDIT_UTILIZATION_PERCENT -> text("Utilización de crédito actual", "Current credit utilization");
            case TRANSFER_ID -> text("Transferencia interna", "Internal transfer");
            case TRANSFER_AMOUNT -> text("Importe transferido", "Transferred amount");
            case TRANSFER_DATE -> text("Fecha de transferencia", "Transfer date");
            case TRANSFER_DAY -> text("Día de transferencia", "Transfer day");
            case TRANSFER_WEEK -> text("Semana ISO de transferencia", "Transfer ISO week");
            case TRANSFER_MONTH -> text("Mes de transferencia", "Transfer month");
            case TRANSFER_YEAR -> text("Año de transferencia", "Transfer year");
            case SOURCE_ACCOUNT_ID -> text("Cuenta de origen", "Source account");
            case SOURCE_ACCOUNT_INSTITUTION -> text("Institución de origen", "Source institution");
            case SOURCE_ACCOUNT_TYPE -> text("Tipo de cuenta de origen", "Source account type");
            case DESTINATION_ACCOUNT_ID -> text("Cuenta de destino", "Destination account");
            case DESTINATION_ACCOUNT_INSTITUTION -> text("Institución de destino", "Destination institution");
            case DESTINATION_ACCOUNT_TYPE -> text("Tipo de cuenta de destino", "Destination account type");
        };
    }

    private SemanticLocalizedText description(SemanticField field) {
        return switch (field) {
            case TRANSACTION_DAY -> text(
                "Agrupa movimientos por fecha de transacción; no cambia el periodo seleccionado.",
                "Groups transactions by transaction date; it does not change the selected period."
            );
            case TRANSACTION_WEEK -> text(
                "Agrupa movimientos por semana ISO y año ISO de la fecha de transacción.",
                "Groups transactions by ISO week and ISO week-year of the transaction date."
            );
            case TRANSACTION_MONTH -> text(
                "Agrupa movimientos por mes calendario de la fecha de transacción.",
                "Groups transactions by calendar month of the transaction date."
            );
            case TRANSACTION_YEAR -> text(
                "Agrupa movimientos por año calendario de la fecha de transacción.",
                "Groups transactions by calendar year of the transaction date."
            );
            case CATEGORY_ID -> text("Categoría asignada directamente al movimiento.", "Category directly assigned to the transaction.");
            case CATEGORY_ROOT -> text("Ancestro raíz de la categoría asignada.", "Ultimate ancestor of the assigned category.");
            case CATEGORY_SUBCATEGORY -> text("Primera rama debajo de la categoría raíz.", "First branch below the root category.");
            case CATEGORY_BRANCH_CHILD -> text(
                "Hijo inmediato de la categoría filtrada; conserva asignaciones directas.",
                "Immediate child of the filtered category; retains direct assignments."
            );
            case TAG_ID -> text("Etiqueta del movimiento; sus grupos pueden solaparse.", "Transaction tag; its groups can overlap.");
            case TRANSFER_LINK_STATUS -> text(
                "Distingue movimientos vinculados a transferencias internas.",
                "Distinguishes legs linked to internal transfers."
            );
            case POSITION_BALANCE -> text(
                "Saldo calculado a una fecha con historial disponible.",
                "Balance calculated at a date with available history."
            );
            case FINANCIAL_POSITION -> text(
                "Saldos registrados compatibles menos deuda registrada de tarjetas, a una misma fecha y sin mezclar monedas.",
                "Compatible recorded balances less recorded credit-card debt, at one date and without mixing currencies."
            );
            case CREDIT_DEBT -> text(
                "Deuda calculada como saldo inicial más salidas menos entradas; puede ser negativa por sobrepago.",
                "Debt calculated as opening balance plus outflows minus inflows; it can be negative after overpayment."
            );
            case CREDIT_LIMIT -> text(
                "Límite registrado hoy; FINTRACK no conserva su historial.",
                "Limit recorded today; FINTRACK does not retain its history."
            );
            case CREDIT_AVAILABLE -> text(
                "Límite actual menos deuda actual; no se conoce para fechas históricas ni sin límite válido.",
                "Current limit minus current debt; unknown for historical dates or without a valid limit."
            );
            case CREDIT_UTILIZATION_PERCENT -> text(
                "Deuda actual dividida entre límite positivo actual, expresada en porcentaje y sin tope artificial.",
                "Current debt divided by the current positive limit, expressed as a percentage and never artificially capped."
            );
            case TRANSFER_AMOUNT -> text(
                "Importe del evento de transferencia único; no es ingreso ni gasto económico.",
                "Amount of the unique transfer event; it is neither economic income nor expense."
            );
            case TRANSFER_DATE -> text(
                "Fecha de la pierna saliente validada; ambas piernas de una transferencia comparten fecha.",
                "Date of the validated outgoing leg; both legs of a transfer share that date."
            );
            case TRANSFER_DAY, TRANSFER_WEEK, TRANSFER_MONTH, TRANSFER_YEAR -> text(
                "Desglose temporal del evento de transferencia único.",
                "Temporal breakdown of the unique transfer event."
            );
            default -> label(field);
        };
    }

    private SemanticLocalizedText text(String es, String en) {
        return new SemanticLocalizedText(es, en);
    }
}
