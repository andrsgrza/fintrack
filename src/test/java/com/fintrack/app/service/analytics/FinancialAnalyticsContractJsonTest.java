package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class FinancialAnalyticsContractJsonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rejectsUnexpectedFieldsOnEveryExternalAnalyticsContract() {
        assertUnknownFieldRejected(
            """
            {"schemaVersion":1,"semanticModel":"FINTRACK_FINANCIAL","measures":["INCOME_TOTAL"],"unknownField":true}
            """,
            QueryDefinition.class
        );
        assertUnknownFieldRejected("{\"schemaVersion\":1,\"unknownField\":true}", VisualizationContext.class);
        assertUnknownFieldRejected("{\"mode\":\"RELATIVE\",\"unknownField\":true}", VisualizationTime.class);
        assertUnknownFieldRejected("{\"schemaVersion\":1,\"unknownField\":true}", FinancialAnalyticsExecutionRequest.class);
        assertUnknownFieldRejected("{\"attributeId\":\"flow\",\"unknownField\":true}", AnalyticalPredicate.class);
        assertUnknownFieldRejected("{\"groupingId\":\"postingDate\",\"unknownField\":true}", AnalyticsSort.class);
    }

    @Test
    void rejectsUnexpectedFieldsInsideNestedExecutionContracts() {
        assertUnknownFieldRejected(
            """
            {
              "schemaVersion": 1,
              "definition": {"schemaVersion": 1, "measures": ["INCOME_TOTAL"], "unknownField": true},
              "context": {"schemaVersion": 1}
            }
            """,
            FinancialAnalyticsExecutionRequest.class
        );
    }

    private void assertUnknownFieldRejected(String json, Class<?> targetType) {
        assertThatThrownBy(() -> objectMapper.readValue(json, targetType)).hasMessageContaining("unknownField");
    }
}
