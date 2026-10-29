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
        definitions.put(SemanticDataset.ACCOUNT_POSITION_SNAPSHOT, accountPositionSnapshot());
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
            default -> label(field);
        };
    }

    private SemanticLocalizedText text(String es, String en) {
        return new SemanticLocalizedText(es, en);
    }
}
