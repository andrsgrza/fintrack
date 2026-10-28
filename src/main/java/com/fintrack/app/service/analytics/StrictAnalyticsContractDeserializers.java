package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Input contracts must remain strict even though the application-wide Spring ObjectMapper accepts unknown fields for
 * legacy resources. These deserializers deliberately scope strictness to the clean Analytics boundary.
 */
public final class StrictAnalyticsContractDeserializers {

    private StrictAnalyticsContractDeserializers() {}

    public static final class QueryDefinitionDeserializer extends StrictObjectDeserializer<QueryDefinition> {

        public QueryDefinitionDeserializer() {
            super(QueryDefinition.class);
        }

        @Override
        public QueryDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(
                parser,
                context,
                Set.of("schemaVersion", "semanticModel", "measures", "groupings", "analyticalPredicates", "dateBasis", "sort", "limit")
            );
            return new QueryDefinition(
                integer(node, "schemaVersion", parser),
                text(node, "semanticModel", parser),
                strings(node, "measures", parser),
                strings(node, "groupings", parser),
                objects(node, "analyticalPredicates", AnalyticalPredicate.class, parser),
                text(node, "dateBasis", parser),
                object(node, "sort", AnalyticsSort.class, parser),
                integer(node, "limit", parser)
            );
        }
    }

    public static final class VisualizationContextDeserializer extends StrictObjectDeserializer<VisualizationContext> {

        public VisualizationContextDeserializer() {
            super(VisualizationContext.class);
        }

        @Override
        public VisualizationContext deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("schemaVersion", "time", "accountIds", "currencyCodes"));
            return new VisualizationContext(
                integer(node, "schemaVersion", parser),
                object(node, "time", VisualizationTime.class, parser),
                longs(node, "accountIds", parser),
                strings(node, "currencyCodes", parser)
            );
        }
    }

    public static final class VisualizationTimeDeserializer extends StrictObjectDeserializer<VisualizationTime> {

        public VisualizationTimeDeserializer() {
            super(VisualizationTime.class);
        }

        @Override
        public VisualizationTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("mode", "relativePeriod", "from", "through"));
            return new VisualizationTime(
                text(node, "mode", parser),
                text(node, "relativePeriod", parser),
                localDate(node, "from", parser),
                localDate(node, "through", parser)
            );
        }
    }

    public static final class ExecutionRequestDeserializer extends StrictObjectDeserializer<FinancialAnalyticsExecutionRequest> {

        public ExecutionRequestDeserializer() {
            super(FinancialAnalyticsExecutionRequest.class);
        }

        @Override
        public FinancialAnalyticsExecutionRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("schemaVersion", "definition", "context"));
            return new FinancialAnalyticsExecutionRequest(
                integer(node, "schemaVersion", parser),
                object(node, "definition", QueryDefinition.class, parser),
                object(node, "context", VisualizationContext.class, parser)
            );
        }
    }

    public static final class PredicateDeserializer extends StrictObjectDeserializer<AnalyticalPredicate> {

        public PredicateDeserializer() {
            super(AnalyticalPredicate.class);
        }

        @Override
        public AnalyticalPredicate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("attributeId", "operatorId", "values"));
            return new AnalyticalPredicate(
                text(node, "attributeId", parser),
                text(node, "operatorId", parser),
                strings(node, "values", parser)
            );
        }
    }

    public static final class SortDeserializer extends StrictObjectDeserializer<AnalyticsSort> {

        public SortDeserializer() {
            super(AnalyticsSort.class);
        }

        @Override
        public AnalyticsSort deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("groupingId", "direction"));
            return new AnalyticsSort(text(node, "groupingId", parser), text(node, "direction", parser));
        }
    }

    private abstract static class StrictObjectDeserializer<T> extends StdDeserializer<T> {

        private StrictObjectDeserializer(Class<T> type) {
            super(type);
        }

        protected ObjectNode object(JsonParser parser, DeserializationContext context, Set<String> allowedFields) throws IOException {
            JsonNode parsed = parser.getCodec().readTree(parser);
            if (!(parsed instanceof ObjectNode node)) {
                throw JsonMappingException.from(
                    parser,
                    "Analytics contract %s must be a JSON object.".formatted(handledType().getSimpleName())
                );
            }
            java.util.Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!allowedFields.contains(field)) {
                    throw JsonMappingException.from(
                        parser,
                        "Unknown field '%s' for Analytics contract %s.".formatted(field, handledType().getSimpleName())
                    );
                }
            }
            return node;
        }

        protected String text(ObjectNode node, String field, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isTextual()) {
                throw invalidType(parser, field, "a string");
            }
            return value.textValue();
        }

        protected Integer integer(ObjectNode node, String field, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                throw invalidType(parser, field, "an integer");
            }
            return value.intValue();
        }

        protected LocalDate localDate(ObjectNode node, String field, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isTextual()) {
                throw invalidType(parser, field, "an ISO-8601 local date");
            }
            try {
                return LocalDate.parse(value.textValue());
            } catch (RuntimeException exception) {
                throw JsonMappingException.from(parser, "Field '%s' must be an ISO-8601 local date.".formatted(field), exception);
            }
        }

        protected List<String> strings(ObjectNode node, String field, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isArray()) {
                throw invalidType(parser, field, "an array of strings");
            }
            List<String> result = new ArrayList<>();
            for (JsonNode element : value) {
                if (!element.isTextual()) {
                    throw invalidType(parser, field, "an array of strings");
                }
                result.add(element.textValue());
            }
            return List.copyOf(result);
        }

        protected List<Long> longs(ObjectNode node, String field, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isArray()) {
                throw invalidType(parser, field, "an array of integer IDs");
            }
            List<Long> result = new ArrayList<>();
            for (JsonNode element : value) {
                if (!element.isIntegralNumber() || !element.canConvertToLong()) {
                    throw invalidType(parser, field, "an array of integer IDs");
                }
                result.add(element.longValue());
            }
            return List.copyOf(result);
        }

        protected <V> V object(ObjectNode node, String field, Class<V> type, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            return parser.getCodec().treeToValue(value, type);
        }

        protected <V> List<V> objects(ObjectNode node, String field, Class<V> type, JsonParser parser) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isArray()) {
                throw invalidType(parser, field, "an array");
            }
            ObjectCodec codec = parser.getCodec();
            List<V> result = new ArrayList<>();
            for (JsonNode element : value) {
                if (!element.isObject()) {
                    throw invalidType(parser, field, "an array of objects");
                }
                result.add(codec.treeToValue(element, type));
            }
            return List.copyOf(result);
        }

        private JsonMappingException invalidType(JsonParser parser, String field, String expected) {
            return JsonMappingException.from(
                parser,
                "Field '%s' in Analytics contract %s must be %s.".formatted(field, handledType().getSimpleName(), expected)
            );
        }
    }
}
