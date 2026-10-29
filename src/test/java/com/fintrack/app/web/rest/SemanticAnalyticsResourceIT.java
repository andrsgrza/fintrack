package com.fintrack.app.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.service.analytics.SemanticAnalyticsSchema;
import com.fintrack.app.service.analytics.SemanticAnalyticsTimePolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract proof for the authenticated, strict semantic Analytics boundary. */
@IntegrationTest
@AutoConfigureMockMvc
class SemanticAnalyticsResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedSemanticCatalogAndExecution() throws Exception {
        mockMvc.perform(get("/api/analytics/semantic/catalog")).andExpect(status().isUnauthorized());
        mockMvc
            .perform(post("/api/analytics/semantic/queries/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(post("/api/analytics/semantic/queries/parse").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void doesNotExposeTheRetiredMetricFirstRoutes() throws Exception {
        mockMvc.perform(get("/api/analytics/catalog")).andExpect(status().isNotFound());
        mockMvc
            .perform(post("/api/analytics/queries/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "user")
    void exposesOnlyTheImplementedSemanticCapabilities() throws Exception {
        mockMvc
            .perform(get("/api/analytics/semantic/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value(SemanticAnalyticsSchema.VERSION))
            .andExpect(jsonPath("$.semanticModel").value(SemanticAnalyticsSchema.SEMANTIC_MODEL))
            .andExpect(jsonPath("$.authenticatedOwnerRequired").value(true))
            .andExpect(jsonPath("$.datasets.length()").value(2))
            .andExpect(jsonPath("$.datasets[0].dataset").value("TRANSACTION_LEDGER"))
            .andExpect(jsonPath("$.datasets[0].grain").value("one FinancialTransaction"))
            .andExpect(jsonPath("$.datasets[1].dataset").value("ACCOUNT_POSITION_SNAPSHOT"))
            .andExpect(jsonPath("$.datasets[1].additivity").value("POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH"))
            .andExpect(jsonPath("$.metrics").doesNotExist())
            .andExpect(jsonPath("$.accountIds").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user")
    void executesTheCanonicalRequestAndRejectsOwnerOrRelationshipSpoofing() throws Exception {
        mockMvc
            .perform(post("/api/analytics/semantic/queries/execute").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value(SemanticAnalyticsSchema.VERSION))
            .andExpect(jsonPath("$.semanticModel").value(SemanticAnalyticsSchema.SEMANTIC_MODEL))
            .andExpect(jsonPath("$.columns[0].id").value("ACCOUNT_INSTITUTION"))
            .andExpect(jsonPath("$.columns[1].id").value("transaction_count"));

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest().replace("\"definition\": {", "\"owner\": \"admin\", \"definition\": {"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEMANTIC_ANALYTICS_MALFORMED_REQUEST"));

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest().replace("TRANSACTION_LEDGER", "UNREGISTERED_DATASET"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEMANTIC_ANALYTICS_MALFORMED_REQUEST"));

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        validRequest()
                            .replace(
                                "\"dataset\": \"TRANSACTION_LEDGER\",",
                                "\"dataset\": \"TRANSACTION_LEDGER\", \"relationship\": \"ALL_TAGS\","
                            )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEMANTIC_ANALYTICS_MALFORMED_REQUEST"));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsAStableSemanticValidationErrorForUnsupportedAggregateCombinations() throws Exception {
        String invalid = validRequest().replace("\"COUNT\", \"field\": \"TRANSACTION_ID\"", "\"AVG\", \"field\": \"TRANSACTION_ID\"");
        mockMvc
            .perform(post("/api/analytics/semantic/queries/execute").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEMANTIC_ANALYTICS_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.diagnostics[0].code").value("INVALID_AGGREGATION"));
    }

    @Test
    @WithMockUser(username = "user")
    void parsesAndFormatsOnlyTheCanonicalSemanticDefinitionWithoutExecutingIt() throws Exception {
        String query =
            "SELECT COUNT(TRANSACTION_ID) AS transaction_count FROM TRANSACTION_LEDGER " +
            "GROUP BY ACCOUNT_INSTITUTION ORDER BY ACCOUNT_INSTITUTION ASC LIMIT 100";

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"" + query + "\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.definition.dataset").value("TRANSACTION_LEDGER"))
            .andExpect(jsonPath("$.definition.timeBinding").value("TRANSACTION_DATE"))
            .andExpect(jsonPath("$.canonicalText").value(org.hamcrest.Matchers.containsString("TIME BINDING TRANSACTION_DATE")));

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/format")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "schemaVersion": 1,
                          "semanticModel": "FINTRACK_FINANCIAL",
                          "dataset": "TRANSACTION_LEDGER",
                          "timeBinding": "TRANSACTION_DATE",
                          "projections": [{"alias": "transaction_count", "function": "COUNT", "field": "TRANSACTION_ID"}],
                          "predicates": [],
                          "groupBy": ["ACCOUNT_INSTITUTION"],
                          "orderBy": [{"field": "ACCOUNT_INSTITUTION", "direction": "ASC"}],
                          "limit": 100
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("TIME BINDING TRANSACTION_DATE")));

        mockMvc
            .perform(
                post("/api/analytics/semantic/queries/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"SELECT COUNT(TRANSACTION_ID) AS c FROM SELECT LIMIT 1\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SEMANTIC_ANALYTICS_DSL_INVALID"))
            .andExpect(jsonPath("$.diagnostics[0].code").value("UNKNOWN_DATASET"));
    }

    private String validRequest() {
        LocalDate today = LocalDate.now(SemanticAnalyticsTimePolicy.BUSINESS_ZONE);
        return """
        {
          "schemaVersion": 1,
          "definition": {
            "schemaVersion": 1,
            "semanticModel": "FINTRACK_FINANCIAL",
            "dataset": "TRANSACTION_LEDGER",
            "timeBinding": "TRANSACTION_DATE",
            "projections": [{"alias": "transaction_count", "function": "COUNT", "field": "TRANSACTION_ID"}],
            "predicates": [],
            "groupBy": ["ACCOUNT_INSTITUTION"],
            "orderBy": [{"field": "ACCOUNT_INSTITUTION", "direction": "ASC"}],
            "limit": 20
          },
          "context": {
            "schemaVersion": 1,
            "time": {"mode": "ABSOLUTE_RANGE", "from": "%s", "through": "%s"},
            "accountIds": [],
            "currencyCodes": []
          }
        }
        """.formatted(today, today);
    }
}
