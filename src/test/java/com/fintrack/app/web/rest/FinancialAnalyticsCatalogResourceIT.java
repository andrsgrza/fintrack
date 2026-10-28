package com.fintrack.app.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.service.analytics.AnalyticsSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Security and disclosure boundary for the catalog-only Analytics endpoint. */
@IntegrationTest
@AutoConfigureMockMvc
class FinancialAnalyticsCatalogResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsUnauthenticatedCatalogRequests() throws Exception {
        mockMvc.perform(get("/api/analytics/catalog")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user")
    void returnsOnlyStaticAuthenticatedCapabilities() throws Exception {
        mockMvc
            .perform(get("/api/analytics/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.schemaVersion").value(AnalyticsSchema.VERSION))
            .andExpect(jsonPath("$.semanticModel").value(AnalyticsSchema.SEMANTIC_MODEL))
            .andExpect(jsonPath("$.metrics.length()").value(16))
            .andExpect(jsonPath("$.attributes.length()").value(15))
            .andExpect(jsonPath("$.accountIds").doesNotExist())
            .andExpect(jsonPath("$.financialTransactions").doesNotExist());
    }
}
