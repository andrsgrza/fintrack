package com.fintrack.app.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.service.analytics.AnalyticsSchema;
import com.fintrack.app.service.analytics.FinancialMetricTimePolicy;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP evidence that the new endpoint accepts only canonical, self-scoped execution payloads. */
@IntegrationTest
@AutoConfigureMockMvc
class FinancialAnalyticsExecutionResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedExecution() throws Exception {
        mockMvc
            .perform(post("/api/analytics/queries/execute").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void returnsTheTypedCanonicalResultForAnAuthenticatedRequest() throws Exception {
        mockMvc
            .perform(post("/api/analytics/queries/execute").contentType(MediaType.APPLICATION_JSON).content(request(false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value(AnalyticsSchema.VERSION))
            .andExpect(jsonPath("$.semanticModel").value(AnalyticsSchema.SEMANTIC_MODEL))
            .andExpect(jsonPath("$.columns[0].id").value("EXPENSE_TOTAL"))
            .andExpect(jsonPath("$.rows[0].measures[0].state").value("NOT_APPLICABLE"));
    }

    @Test
    @WithMockUser(username = "user")
    void rejectsOwnerSpoofingAndUnknownCanonicalFieldsWithStableFourHundredResponse() throws Exception {
        mockMvc
            .perform(post("/api/analytics/queries/execute").contentType(MediaType.APPLICATION_JSON).content(request(true)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ANALYTICS_MALFORMED_REQUEST"))
            .andExpect(jsonPath("$.diagnostics[0].code").value("MALFORMED_REQUEST"));
    }

    private String request(boolean includeSpoofedOwner) {
        LocalDate today = LocalDate.now(FinancialMetricTimePolicy.BUSINESS_ZONE);
        String owner = includeSpoofedOwner ? ",\"owner\":\"admin\"" : "";
        return """
        {
          "schemaVersion": 1,
          "definition": {
            "schemaVersion": 1,
            "semanticModel": "FINTRACK_FINANCIAL",
            "measures": ["EXPENSE_TOTAL"],
            "groupings": [],
            "analyticalPredicates": [],
            "dateBasis": "TRANSACTION_DATE",
            "limit": 20
          },
          "context": {
            "schemaVersion": 1,
            "time": {"mode": "ABSOLUTE_RANGE", "from": "%s", "through": "%s"},
            "accountIds": [],
            "currencyCodes": []%s
          }
        }
        """.formatted(today, today, owner);
    }
}
