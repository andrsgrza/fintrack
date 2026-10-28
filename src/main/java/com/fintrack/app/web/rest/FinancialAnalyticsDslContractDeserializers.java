package com.fintrack.app.web.rest;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.service.analytics.QueryDefinition;
import java.io.IOException;
import java.util.Set;

/** Strict JSON boundary for the text-only Analytics DSL endpoints. */
final class FinancialAnalyticsDslContractDeserializers {

    private FinancialAnalyticsDslContractDeserializers() {}

    static final class ParseRequestDeserializer extends StrictDeserializer<FinancialAnalyticsDslParseRequest> {

        ParseRequestDeserializer() {
            super(FinancialAnalyticsDslParseRequest.class);
        }

        @Override
        public FinancialAnalyticsDslParseRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, Set.of("text"));
            return new FinancialAnalyticsDslParseRequest(text(node, "text", parser));
        }
    }

    static final class FormatRequestDeserializer extends StrictDeserializer<FinancialAnalyticsDslFormatRequest> {

        FormatRequestDeserializer() {
            super(FinancialAnalyticsDslFormatRequest.class);
        }

        @Override
        public FinancialAnalyticsDslFormatRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            ObjectNode node = object(parser, Set.of("definition"));
            JsonNode definition = node.get("definition");
            if (definition == null || definition.isNull()) {
                return new FinancialAnalyticsDslFormatRequest(null);
            }
            if (!definition.isObject()) {
                throw invalidType(parser, "definition", "an object");
            }
            return new FinancialAnalyticsDslFormatRequest(parser.getCodec().treeToValue(definition, QueryDefinition.class));
        }
    }

    private abstract static class StrictDeserializer<T> extends StdDeserializer<T> {

        private StrictDeserializer(Class<T> type) {
            super(type);
        }

        protected ObjectNode object(JsonParser parser, Set<String> allowedFields) throws IOException {
            JsonNode parsed = parser.getCodec().readTree(parser);
            if (!(parsed instanceof ObjectNode node)) {
                throw JsonMappingException.from(parser, "Analytics DSL request must be a JSON object.");
            }
            java.util.Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!allowedFields.contains(field)) {
                    throw JsonMappingException.from(parser, "Unknown field '%s' for Analytics DSL request.".formatted(field));
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

        protected JsonMappingException invalidType(JsonParser parser, String field, String expected) {
            return JsonMappingException.from(parser, "Field '%s' must be %s.".formatted(field, expected));
        }
    }
}
