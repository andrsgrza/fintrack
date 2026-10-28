package com.fintrack.app.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP contract coverage for the authenticated, non-executing Analytics DSL endpoints. */
@IntegrationTest
@AutoConfigureMockMvc
class FinancialAnalyticsDslResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedParsingAndFormatting() throws Exception {
        mockMvc
            .perform(
                post("/api/analytics/queries/parse").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"SELECT EXPENSE_TOTAL\"}")
            )
            .andExpect(status().isUnauthorized());
        mockMvc
            .perform(post("/api/analytics/queries/format").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void parsesAndFormatsOnlyTheCanonicalDefinitionWithoutAnExecutionContext() throws Exception {
        mockMvc
            .perform(
                post("/api/analytics/queries/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"text\":\"SELECT EXPENSE_TOTAL BY MONTH WHERE FLOW = OUT USING POSTING_DATE ORDER BY MONTH DESC LIMIT 25\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.definition.measures[0]").value("EXPENSE_TOTAL"))
            .andExpect(jsonPath("$.definition.groupings[0]").value("MONTH"))
            .andExpect(jsonPath("$.definition.analyticalPredicates[0].operatorId").value("EQUALS"))
            .andExpect(
                jsonPath("$.canonicalText").value(
                    "SELECT EXPENSE_TOTAL BY MONTH WHERE FLOW EQUALS OUT USING POSTING_DATE ORDER BY MONTH DESC LIMIT 25"
                )
            );

        mockMvc
            .perform(post("/api/analytics/queries/format").contentType(MediaType.APPLICATION_JSON).content(formatRequest()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.text").value("SELECT EXPENSE_TOTAL BY MONTH USING TRANSACTION_DATE ORDER BY MONTH ASC LIMIT 100"));
    }

    @Test
    @WithMockUser(username = "user")
    void returnsTypedDslDiagnosticsWithSourcePositionsAndRejectsSqlLookingInput() throws Exception {
        mockMvc
            .perform(
                post("/api/analytics/queries/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"SELECT EXPENSE_TOTAL FROM financial_transaction\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ANALYTICS_DSL_INVALID"))
            .andExpect(jsonPath("$.diagnostics[0].code").value("TRAILING_GARBAGE"))
            .andExpect(jsonPath("$.diagnostics[0].line").value(1))
            .andExpect(jsonPath("$.diagnostics[0].column").value(22));
    }

    @Test
    @WithMockUser(username = "user")
    void rejectsSpoofedOwnerFieldsInsteadOfIgnoringThem() throws Exception {
        mockMvc
            .perform(
                post("/api/analytics/queries/parse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"SELECT EXPENSE_TOTAL\",\"owner\":\"admin\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ANALYTICS_MALFORMED_REQUEST"));
    }

    private String formatRequest() {
        return """
        {
          "definition": {
            "schemaVersion": 1,
            "semanticModel": "FINTRACK_FINANCIAL",
            "measures": ["EXPENSE_TOTAL"],
            "groupings": ["MONTH"],
            "analyticalPredicates": [],
            "dateBasis": "TRANSACTION_DATE",
            "limit": 100
          }
        }
        """;
    }
}
