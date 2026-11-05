package com.fintrack.app.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.PersonalDashboard;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.PersonalDashboardRepository;
import com.fintrack.app.repository.PersonalDashboardRequestRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Real PostgreSQL contract tests for owner-scoped personal-dashboard persistence. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "user")
class PersonalDashboardResourceIT {

    private static final String URL = "/api/personal-dashboards";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersonalDashboardRepository dashboardRepository;

    @Autowired
    private PersonalDashboardRequestRepository requestRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithAnonymousUser
    void rejectsUnauthenticatedPersonalDashboardAccess() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsAndIdempotentlyReplaysDashboardWithRelativeContext() throws Exception {
        JsonNode created = create("Flujo mensual", "create-flujo-1", relativeContext());
        long id = created.path("id").asLong();

        JsonNode replayed = body(
            mockMvc
                .perform(
                    post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("Flujo mensual", "create-flujo-1", relativeContext()))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.defaultContext.time.mode").value("RELATIVE"))
                .andExpect(jsonPath("$.defaultContext.time.relativePeriod").value("THIS_MONTH"))
                .andReturn()
        );
        assertThat(replayed.path("id").asLong()).isEqualTo(id);
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(1);
        assertThat(requestRepository.count()).isEqualTo(1);

        mockMvc
            .perform(get(URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].widgetCount").value(0))
            .andExpect(jsonPath("$[0].defaultContext").doesNotExist());

        mockMvc
            .perform(
                post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(" flujo   mensual ", "create-flujo-2", relativeContext()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
    }

    @Test
    void storesCanonicalWidgetsAndAtomicallyPersistsLayoutWithoutContextInTheAst() throws Exception {
        JsonNode dashboard = create("Análisis", "create-analysis-1", relativeContext());
        long id = dashboard.path("id").asLong();
        long version = dashboard.path("version").asLong();

        JsonNode afterFirst = addWidget(
            id,
            version,
            "Conteo por institución",
            "add-widget-1",
            countByInstitution(),
            "BAR",
            "COMPACT",
            true
        );
        long firstWidgetId = afterFirst.path("widgets").get(0).path("id").asLong();
        long afterFirstVersion = afterFirst.path("version").asLong();
        JsonNode afterSecond = addWidget(id, afterFirstVersion, "Total de gastos", "add-widget-2", expenseTotal(), "KPI", "MEDIUM", true);
        long secondWidgetId = afterSecond.path("widgets").get(1).path("id").asLong();

        ObjectNode layout = objectMapper.createObjectNode().put("expectedVersion", afterSecond.path("version").asLong());
        ArrayNode widgets = layout.putArray("widgets");
        widgets.addObject().put("widgetId", secondWidgetId).put("width", "FULL").put("visible", false);
        widgets.addObject().put("widgetId", firstWidgetId).put("width", "COMPACT").put("visible", true);
        JsonNode reordered = body(
            mockMvc
                .perform(
                    put(URL + "/{id}/layout", id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(layout))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].id").value(secondWidgetId))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[0].width").value("FULL"))
                .andExpect(jsonPath("$.widgets[0].visible").value(false))
                .andExpect(jsonPath("$.widgets[1].id").value(firstWidgetId))
                .andReturn()
        );
        assertThat(reordered.path("widgets")).hasSize(2);
        entityManager.flush();
        entityManager.clear();
        PersonalDashboard persisted = dashboardRepository.findOneWithWidgetsByIdAndUserLogin(id, "user").orElseThrow();
        assertThat(persisted.getWidgets()).extracting(widget -> widget.getPosition()).containsExactly(0, 1);
        assertThat(persisted.getWidgets().get(0).getQueryDefinitionJson()).doesNotContain("context");
        assertThat(persisted.getWidgets().get(0).getVisualizationOptionsJson()).isEqualTo("{}");
    }

    @Test
    void rejectsStaleMutationsUnsupportedWidgetShapesAndConflictingIdempotencyKeys() throws Exception {
        JsonNode dashboard = create("Protegido", "create-protected-1", relativeContext());
        long id = dashboard.path("id").asLong();
        long originalVersion = dashboard.path("version").asLong();
        JsonNode saved = addWidget(id, originalVersion, "Conteo", "add-protected-1", countByInstitution(), "BAR", "COMPACT", true);

        ObjectNode staleUpdate = objectMapper.createObjectNode();
        staleUpdate.put("expectedVersion", originalVersion);
        staleUpdate.put("name", "No debe sobrescribir");
        staleUpdate.set("defaultContext", relativeContext());
        mockMvc
            .perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(staleUpdate)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        ObjectNode incompatible = widgetPayload(
            saved.path("version").asLong(),
            "Indicador inválido",
            "add-protected-2",
            countByInstitution(),
            "KPI",
            "COMPACT",
            true
        );
        mockMvc
            .perform(
                post(URL + "/{id}/widgets", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(incompatible))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc
            .perform(
                post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("Protegido cambiado", "create-protected-1", relativeContext()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void rejectsForeignAccountCategoryAndTagReferencesBeforePersistingAWidget() throws Exception {
        User other = UserResourceIT.createEntity();
        entityManager.persist(other);
        FinancialAccount foreignAccount = FinancialAccountResourceIT.createEntity(entityManager);
        foreignAccount.setUser(other);
        entityManager.persist(foreignAccount);
        Category foreignCategory = CategoryResourceIT.createEntity(entityManager);
        foreignCategory.setUser(other);
        entityManager.persist(foreignCategory);
        Tag foreignTag = TagResourceIT.createEntity(entityManager);
        foreignTag.setUser(other);
        entityManager.persist(foreignTag);
        entityManager.flush();

        ObjectNode foreignContext = relativeContext();
        foreignContext.withArray("accountIds").add(foreignAccount.getId());
        mockMvc
            .perform(
                post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload("Cuenta ajena", "create-foreign-context", foreignContext))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        JsonNode dashboard = create("Referencias", "create-references-1", relativeContext());
        ObjectNode definition = expenseTotal();
        definition
            .withArray("predicates")
            .addObject()
            .put("field", "CATEGORY_ID")
            .put("operator", "EQUALS")
            .putArray("values")
            .add(foreignCategory.getId().toString());
        definition
            .withArray("predicates")
            .addObject()
            .put("field", "TAG_ID")
            .put("operator", "HAS_ANY")
            .putArray("values")
            .add(foreignTag.getId().toString());
        mockMvc
            .perform(
                post(URL + "/{id}/widgets", dashboard.path("id").asLong())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            widgetPayload(
                                dashboard.path("version").asLong(),
                                "No guardar",
                                "add-foreign-references-1",
                                definition,
                                "KPI",
                                "MEDIUM",
                                true
                            )
                        )
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        assertThat(
            dashboardRepository.findOneWithWidgetsByIdAndUserLogin(dashboard.path("id").asLong(), "user").orElseThrow().getWidgets()
        ).isEmpty();
    }

    @Test
    void updatesTheSameWidgetWithoutChangingItsIdentityPositionOrControlledLayout() throws Exception {
        JsonNode dashboard = create("Editar widget", "create-edit-widget-1", relativeContext());
        JsonNode withWidget = addWidget(
            dashboard.path("id").asLong(),
            dashboard.path("version").asLong(),
            "Conteo original",
            "add-edit-widget-1",
            countByInstitution(),
            "BAR",
            "FULL",
            false
        );
        long dashboardId = withWidget.path("id").asLong();
        long widgetId = withWidget.path("widgets").get(0).path("id").asLong();
        long version = withWidget.path("version").asLong();

        ObjectNode update = widgetUpdatePayload(version, "Gastos actualizados", expenseTotal(), "KPI", "FULL", false);
        JsonNode updated = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, widgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets.length()").value(1))
                .andExpect(jsonPath("$.widgets[0].id").value(widgetId))
                .andExpect(jsonPath("$.widgets[0].title").value("Gastos actualizados"))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[0].width").value("FULL"))
                .andExpect(jsonPath("$.widgets[0].visible").value(false))
                .andExpect(jsonPath("$.widgets[0].definition.predicates[0].field").value("TRANSACTION_FLOW"))
                .andReturn()
        );
        assertThat(updated.path("widgets").get(0).path("definition").toString()).doesNotContain("context");

        mockMvc
            .perform(
                put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, widgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            widgetUpdatePayload(version, "No sobrescribir", expenseTotal(), "KPI", "FULL", false)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        entityManager.flush();
        entityManager.clear();
        PersonalDashboard persisted = dashboardRepository.findOneWithWidgetsByIdAndUserLogin(dashboardId, "user").orElseThrow();
        assertThat(persisted.getWidgets()).hasSize(1);
        assertThat(persisted.getWidgets().get(0).getId()).isEqualTo(widgetId);
        assertThat(persisted.getWidgets().get(0).getPosition()).isZero();
        assertThat(persisted.getWidgets().get(0).getWidth().name()).isEqualTo("FULL");
        assertThat(persisted.getWidgets().get(0).getVisible()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void keepsAnAdministratorSelfScopedAndDoesNotLeakAnotherUsersDashboard() throws Exception {
        User user = entityManager.createQuery("select user from User user where user.login = 'user'", User.class).getSingleResult();
        PersonalDashboard foreign = new PersonalDashboard();
        foreign.setUser(user);
        foreign.setName("Privado");
        foreign.setNormalizedName("privado");
        foreign.setDefaultContextJson(objectMapper.writeValueAsString(relativeContext()));
        foreign.setSchemaVersion(1);
        foreign.setCreatedAt(Instant.now());
        foreign.setUpdatedAt(Instant.now());
        foreign = dashboardRepository.saveAndFlush(foreign);

        mockMvc.perform(get(URL + "/{id}", foreign.getId())).andExpect(status().isNotFound());
        mockMvc
            .perform(delete(URL + "/{id}", foreign.getId()).param("expectedVersion", foreign.getVersion().toString()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteCascadesOnlyToWidgetsAndLeavesFinancialDomainUntouched() throws Exception {
        JsonNode dashboard = create("Temporal", "create-temporal-1", relativeContext());
        JsonNode withWidget = addWidget(
            dashboard.path("id").asLong(),
            dashboard.path("version").asLong(),
            "Gastos",
            "add-temporal-1",
            expenseTotal(),
            "KPI",
            "MEDIUM",
            true
        );
        long id = withWidget.path("id").asLong();
        mockMvc
            .perform(delete(URL + "/{id}", id).param("expectedVersion", withWidget.path("version").asText()))
            .andExpect(status().isNoContent());
        assertThat(dashboardRepository.findById(id)).isEmpty();
        assertThat(requestRepository.count()).isEqualTo(2); // receipts retain only IDs and cannot cascade to financial data.
    }

    @Test
    void copiesAnOfficialTemplateAtomicallyWithValidatedIndependentWidgetsAndIdempotentReplay() throws Exception {
        ObjectNode copy = templateCopyPayload("financial-overview", "Mi resumen", "copy-overview-1");
        JsonNode created = body(
            mockMvc
                .perform(post(URL + "/from-template").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(copy)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mi resumen"))
                .andExpect(jsonPath("$.widgets.length()").value(2))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[1].position").value(1))
                .andReturn()
        );
        long copiedId = created.path("id").asLong();
        long firstWidgetId = created.path("widgets").get(0).path("id").asLong();
        assertThat(dashboardRepository.findOneWithWidgetsByIdAndUserLogin(copiedId, "user").orElseThrow().getWidgets()).allSatisfy(widget ->
            assertThat(widget.getQueryDefinitionJson()).doesNotContain("context")
        );

        mockMvc
            .perform(post(URL + "/from-template").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(copy)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(copiedId));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(1);

        ObjectNode independent = templateCopyPayload("financial-overview", "Mi segundo resumen", "copy-overview-2");
        JsonNode second = body(
            mockMvc
                .perform(
                    post(URL + "/from-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(independent))
                )
                .andExpect(status().isCreated())
                .andReturn()
        );
        assertThat(second.path("id").asLong()).isNotEqualTo(copiedId);
        assertThat(second.path("widgets").get(0).path("id").asLong()).isNotEqualTo(firstWidgetId);

        ObjectNode rejected = templateCopyPayload("financial-overview", "No parcial", "copy-overview-invalid");
        ((ObjectNode) rejected.withArray("widgets").get(0)).put("visualizationType", "KPI");
        mockMvc
            .perform(post(URL + "/from-template").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(rejected)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(2);
    }

    private JsonNode create(String name, String key, JsonNode context) throws Exception {
        return body(
            mockMvc
                .perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(createPayload(name, key, context)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andReturn()
        );
    }

    private JsonNode addWidget(
        long dashboardId,
        long expectedVersion,
        String title,
        String key,
        JsonNode definition,
        String visualizationType,
        String width,
        boolean visible
    ) throws Exception {
        return body(
            mockMvc
                .perform(
                    post(URL + "/{id}/widgets", dashboardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsBytes(
                                widgetPayload(expectedVersion, title, key, definition, visualizationType, width, visible)
                            )
                        )
                )
                .andExpect(status().isOk())
                .andReturn()
        );
    }

    private byte[] createPayload(String name, String key, JsonNode context) throws Exception {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("name", name);
        command.put("clientRequestId", key);
        command.set("defaultContext", context);
        return objectMapper.writeValueAsBytes(command);
    }

    private ObjectNode widgetPayload(
        long expectedVersion,
        String title,
        String key,
        JsonNode definition,
        String visualizationType,
        String width,
        boolean visible
    ) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("expectedVersion", expectedVersion);
        command.put("title", title);
        command.put("clientRequestId", key);
        command.set("definition", definition);
        command.put("visualizationType", visualizationType);
        command.set("visualizationOptions", objectMapper.createObjectNode());
        command.put("width", width);
        command.put("visible", visible);
        return command;
    }

    private ObjectNode widgetUpdatePayload(
        long expectedVersion,
        String title,
        JsonNode definition,
        String visualizationType,
        String width,
        boolean visible
    ) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("expectedVersion", expectedVersion);
        command.put("title", title);
        command.set("definition", definition);
        command.put("visualizationType", visualizationType);
        command.set("visualizationOptions", objectMapper.createObjectNode());
        command.put("width", width);
        command.put("visible", visible);
        return command;
    }

    private ObjectNode templateCopyPayload(String templateId, String name, String key) {
        ObjectNode command = objectMapper.createObjectNode();
        command.put("templateId", templateId);
        command.put("name", name);
        command.put("clientRequestId", key);
        command.set("defaultContext", relativeContext());
        ArrayNode widgets = command.putArray("widgets");
        ObjectNode count = widgets.addObject();
        count.put("title", "Conteo por institución");
        count.set("definition", countByInstitution());
        count.put("visualizationType", "BAR");
        count.set("visualizationOptions", objectMapper.createObjectNode());
        count.put("width", "MEDIUM");
        count.put("visible", true);
        ObjectNode expenses = widgets.addObject();
        expenses.put("title", "Gastos");
        expenses.set("definition", expenseTotal());
        expenses.put("visualizationType", "KPI");
        expenses.set("visualizationOptions", objectMapper.createObjectNode());
        expenses.put("width", "COMPACT");
        expenses.put("visible", true);
        return command;
    }

    private ObjectNode relativeContext() {
        ObjectNode context = objectMapper.createObjectNode().put("schemaVersion", 1);
        context.putObject("time").put("mode", "RELATIVE").put("relativePeriod", "THIS_MONTH");
        context.putArray("accountIds");
        context.putArray("currencyCodes");
        return context;
    }

    private ObjectNode countByInstitution() {
        ObjectNode definition = definition("TRANSACTION_LEDGER", "TRANSACTION_DATE");
        definition
            .putArray("projections")
            .addObject()
            .put("alias", "conteo")
            .put("function", "COUNT")
            .put("field", "TRANSACTION_ID")
            .putArray("conditions");
        definition.putArray("groupBy").add("ACCOUNT_INSTITUTION");
        definition.putArray("orderBy").addObject().put("field", "ACCOUNT_INSTITUTION").put("direction", "ASC");
        return definition;
    }

    private ObjectNode expenseTotal() {
        ObjectNode definition = definition("TRANSACTION_LEDGER", "TRANSACTION_DATE");
        definition
            .putArray("projections")
            .addObject()
            .put("alias", "gastos")
            .put("function", "SUM")
            .put("field", "TRANSACTION_AMOUNT")
            .putArray("conditions");
        ArrayNode predicates = definition.putArray("predicates");
        predicates.addObject().put("field", "TRANSACTION_FLOW").put("operator", "EQUALS").putArray("values").add("OUT");
        predicates.addObject().put("field", "TRANSFER_LINK_STATUS").put("operator", "EQUALS").putArray("values").add("UNLINKED");
        definition.putArray("groupBy");
        definition.putArray("orderBy");
        return definition;
    }

    private ObjectNode definition(String dataset, String timeBinding) {
        return objectMapper
            .createObjectNode()
            .put("schemaVersion", 1)
            .put("semanticModel", "FINTRACK_FINANCIAL")
            .put("dataset", dataset)
            .put("timeBinding", timeBinding)
            .put("limit", 100);
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
