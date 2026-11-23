package com.fintrack.app.service.budget;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Budget amounts cross the HTTP boundary as plain decimal strings. JSON numbers would be parsed as JavaScript
 * floating-point values by the browser and could lose cents before a Budget form or view has a chance to validate
 * them. This is deliberately Budget-scoped; it does not alter FINTRACK's general BigDecimal JSON policy.
 */
public final class BudgetDecimalJson {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");

    private BudgetDecimalJson() {}

    public static final class Serializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, com.fasterxml.jackson.core.JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
            generator.writeString(value.toPlainString());
        }
    }

    /**
     * Accepts a textual decimal (the public Budget contract) and legacy JSON numeric literals without converting via
     * double. Scientific notation, locale-formatted values, and malformed input are rejected before a command can
     * reach persistence. The shared service validator subsequently applies positivity, scale, and precision rules.
     */
    public static final class TargetAmountDeserializer extends JsonDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
            if (parser.currentToken() != JsonToken.VALUE_STRING && !parser.currentToken().isNumeric()) {
                throw context.weirdStringException(
                    parser.getValueAsString(),
                    BigDecimal.class,
                    "targetAmount must be a plain decimal amount."
                );
            }
            String raw = parser.getText();
            if (raw == null || !PLAIN_DECIMAL.matcher(raw).matches()) {
                throw context.weirdStringException(raw, BigDecimal.class, "targetAmount must be a plain decimal amount.");
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException exception) {
                throw context.weirdStringException(raw, BigDecimal.class, "targetAmount must be a valid decimal amount.");
            }
        }
    }

    /**
     * Thresholds use the same lossless plain-decimal transport as Budget amounts, while retaining a specific
     * validation message for this independent notification-preference contract. Numeric literals remain accepted
     * only for older API clients; all current Budget UI clients send strings.
     */
    public static final class ThresholdPercentageDeserializer extends JsonDeserializer<BigDecimal> {

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_NULL) return null;
            if (parser.currentToken() != JsonToken.VALUE_STRING && !parser.currentToken().isNumeric()) {
                throw context.weirdStringException(
                    parser.getValueAsString(),
                    BigDecimal.class,
                    "threshold percentage must be a plain decimal."
                );
            }
            String raw = parser.getText();
            if (raw == null || !PLAIN_DECIMAL.matcher(raw).matches()) {
                throw context.weirdStringException(raw, BigDecimal.class, "threshold percentage must be a plain decimal.");
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException exception) {
                throw context.weirdStringException(raw, BigDecimal.class, "threshold percentage must be a valid decimal.");
            }
        }
    }
}
