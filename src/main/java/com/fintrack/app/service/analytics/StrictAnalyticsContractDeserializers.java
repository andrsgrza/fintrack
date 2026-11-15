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

/** Input contracts remain strict even though the application-wide mapper accepts unknown fields elsewhere. */
public final class StrictAnalyticsContractDeserializers {

    private StrictAnalyticsContractDeserializers() {}

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

    public static final class SemanticQueryDefinitionDeserializer extends StrictObjectDeserializer<SemanticQueryDefinition> {

        public SemanticQueryDefinitionDeserializer() {
            super(SemanticQueryDefinition.class);
        }

        @Override
        public SemanticQueryDefinition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(
                parser,
                context,
                Set.of(
                    "schemaVersion",
                    "semanticModel",
                    "dataset",
                    "timeBinding",
                    "projections",
                    "predicates",
                    "groupBy",
                    "orderBy",
                    "limit"
                )
            );
            return new SemanticQueryDefinition(
                integer(node, "schemaVersion", parser),
                text(node, "semanticModel", parser),
                enumeration(node, "dataset", SemanticDataset.class, parser),
                enumeration(node, "timeBinding", SemanticField.class, parser),
                objects(node, "projections", SemanticAggregateProjection.class, parser),
                objects(node, "predicates", SemanticPredicate.class, parser),
                enumerations(node, "groupBy", SemanticField.class, parser),
                objects(node, "orderBy", SemanticOrder.class, parser),
                integer(node, "limit", parser)
            );
        }
    }

    public static final class SemanticAggregateProjectionDeserializer extends StrictObjectDeserializer<SemanticAggregateProjection> {

        public SemanticAggregateProjectionDeserializer() {
            super(SemanticAggregateProjection.class);
        }

        @Override
        public SemanticAggregateProjection deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("alias", "function", "field", "conditions"));
            return new SemanticAggregateProjection(
                text(node, "alias", parser),
                enumeration(node, "function", SemanticAggregateFunction.class, parser),
                enumeration(node, "field", SemanticField.class, parser),
                objects(node, "conditions", SemanticPredicate.class, parser)
            );
        }
    }

    public static final class SemanticPredicateDeserializer extends StrictObjectDeserializer<SemanticPredicate> {

        public SemanticPredicateDeserializer() {
            super(SemanticPredicate.class);
        }

        @Override
        public SemanticPredicate deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("field", "operator", "values"));
            return new SemanticPredicate(
                enumeration(node, "field", SemanticField.class, parser),
                enumeration(node, "operator", SemanticPredicateOperator.class, parser),
                strings(node, "values", parser)
            );
        }
    }

    public static final class SemanticOrderDeserializer extends StrictObjectDeserializer<SemanticOrder> {

        public SemanticOrderDeserializer() {
            super(SemanticOrder.class);
        }

        @Override
        public SemanticOrder deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("field", "projectionAlias", "direction"));
            return new SemanticOrder(
                enumeration(node, "field", SemanticField.class, parser),
                text(node, "projectionAlias", parser),
                enumeration(node, "direction", SemanticSortDirection.class, parser)
            );
        }
    }

    public static final class SemanticExecutionRequestDeserializer extends StrictObjectDeserializer<SemanticAnalyticsExecutionRequest> {

        public SemanticExecutionRequestDeserializer() {
            super(SemanticAnalyticsExecutionRequest.class);
        }

        @Override
        public SemanticAnalyticsExecutionRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("schemaVersion", "definition", "context"));
            return new SemanticAnalyticsExecutionRequest(
                integer(node, "schemaVersion", parser),
                object(node, "definition", SemanticQueryDefinition.class, parser),
                object(node, "context", VisualizationContext.class, parser)
            );
        }
    }

    public static final class SemanticParseRequestDeserializer extends StrictObjectDeserializer<SemanticAnalyticsDslParseRequest> {

        public SemanticParseRequestDeserializer() {
            super(SemanticAnalyticsDslParseRequest.class);
        }

        @Override
        public SemanticAnalyticsDslParseRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, context, Set.of("text"));
            return new SemanticAnalyticsDslParseRequest(text(node, "text", parser));
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

        protected <E extends Enum<E>> E enumeration(ObjectNode node, String field, Class<E> type, JsonParser parser) throws IOException {
            String value = text(node, field, parser);
            if (value == null) {
                return null;
            }
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException exception) {
                throw JsonMappingException.from(
                    parser,
                    "Field '%s' in Analytics contract %s has an unknown %s value.".formatted(
                            field,
                            handledType().getSimpleName(),
                            type.getSimpleName()
                        ),
                    exception
                );
            }
        }

        protected <E extends Enum<E>> List<E> enumerations(ObjectNode node, String field, Class<E> type, JsonParser parser)
            throws IOException {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            if (!value.isArray()) {
                throw invalidType(parser, field, "an array of " + type.getSimpleName() + " values");
            }
            List<E> result = new ArrayList<>();
            for (JsonNode element : value) {
                if (!element.isTextual()) {
                    throw invalidType(parser, field, "an array of " + type.getSimpleName() + " values");
                }
                try {
                    result.add(Enum.valueOf(type, element.textValue()));
                } catch (IllegalArgumentException exception) {
                    throw JsonMappingException.from(
                        parser,
                        "Field '%s' in Analytics contract %s has an unknown %s value.".formatted(
                                field,
                                handledType().getSimpleName(),
                                type.getSimpleName()
                            ),
                        exception
                    );
                }
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
