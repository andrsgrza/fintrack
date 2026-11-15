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
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
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

        JsonNode afterFirst = addWidget(id, version, "Conteo por institución", "add-widget-1", countByInstitution(), "BAR", "MEDIUM", true);
        long firstWidgetId = afterFirst.path("widgets").get(0).path("id").asLong();
        long afterFirstVersion = afterFirst.path("version").asLong();
        JsonNode afterSecond = addWidget(id, afterFirstVersion, "Total de gastos", "add-widget-2", expenseTotal(), "KPI", "MEDIUM", true);
        long secondWidgetId = afterSecond.path("widgets").get(1).path("id").asLong();

        ObjectNode layout = objectMapper.createObjectNode().put("expectedVersion", afterSecond.path("version").asLong());
        ArrayNode widgets = layout.putArray("widgets");
        widgets.addObject().put("widgetId", secondWidgetId).put("width", "FULL").put("visible", false);
        widgets.addObject().put("widgetId", firstWidgetId).put("width", "MEDIUM").put("visible", true);
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
        assertThat(persisted.getWidgets())
            .extracting(widget -> widget.getWidth())
            .containsExactly(PersonalDashboardWidgetWidth.FULL, PersonalDashboardWidgetWidth.MEDIUM);
        assertThat(persisted.getWidgets()).extracting(widget -> widget.getVisible()).containsExactly(false, true);
        assertThat(persisted.getWidgets().get(0).getQueryDefinitionJson()).doesNotContain("context");
        assertThat(persisted.getWidgets().get(0).getVisualizationOptionsJson()).isEqualTo("{}");
    }

    @Test
    void rejectsStaleMutationsUnsupportedWidgetShapesAndConflictingIdempotencyKeys() throws Exception {
        JsonNode dashboard = create("Protegido", "create-protected-1", relativeContext());
        long id = dashboard.path("id").asLong();
        long originalVersion = dashboard.path("version").asLong();
        JsonNode saved = addWidget(id, originalVersion, "Conteo", "add-protected-1", countByInstitution(), "BAR", "MEDIUM", true);

        ObjectNode compactChartLayout = objectMapper.createObjectNode().put("expectedVersion", saved.path("version").asLong());
        compactChartLayout
            .putArray("widgets")
            .addObject()
            .put("widgetId", saved.path("widgets").get(0).path("id").asLong())
            .put("width", "COMPACT")
            .put("visible", true);
        mockMvc
            .perform(
                put(URL + "/{id}/layout", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(compactChartLayout))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LAYOUT"));

        long widgetId = saved.path("widgets").get(0).path("id").asLong();
        ObjectNode validLayout = objectMapper.createObjectNode().put("expectedVersion", saved.path("version").asLong());
        validLayout.putArray("widgets").addObject().put("widgetId", widgetId).put("width", "MEDIUM").put("visible", true);
        JsonNode layoutSaved = body(
            mockMvc
                .perform(
                    put(URL + "/{id}/layout", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(validLayout))
                )
                .andExpect(status().isOk())
                .andReturn()
        );

        mockMvc
            .perform(
                put(URL + "/{id}/layout", id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(validLayout))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        ObjectNode duplicateLayout = objectMapper.createObjectNode().put("expectedVersion", layoutSaved.path("version").asLong());
        ArrayNode duplicateWidgets = duplicateLayout.putArray("widgets");
        duplicateWidgets.addObject().put("widgetId", widgetId).put("width", "MEDIUM").put("visible", true);
        duplicateWidgets.addObject().put("widgetId", widgetId).put("width", "MEDIUM").put("visible", true);
        assertThat(duplicateWidgets).hasSize(2);
        assertThat(objectMapper.readTree(objectMapper.writeValueAsBytes(duplicateLayout)).path("widgets")).hasSize(2);
        mockMvc
            .perform(
                put(URL + "/{id}/layout", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicateLayout))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LAYOUT"));

        ObjectNode incompleteLayout = objectMapper.createObjectNode().put("expectedVersion", layoutSaved.path("version").asLong());
        incompleteLayout.putArray("widgets");
        mockMvc
            .perform(
                put(URL + "/{id}/layout", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(incompleteLayout))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LAYOUT"));

        ObjectNode staleUpdate = objectMapper.createObjectNode();
        staleUpdate.put("expectedVersion", originalVersion);
        staleUpdate.put("name", "No debe sobrescribir");
        staleUpdate.set("defaultContext", relativeContext());
        mockMvc
            .perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(staleUpdate)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        ObjectNode incompatible = widgetPayload(
            layoutSaved.path("version").asLong(),
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

        ObjectNode nonTemporalLine = widgetPayload(
            layoutSaved.path("version").asLong(),
            "Línea inválida",
            "add-protected-non-temporal-line",
            countByInstitution(),
            "LINE",
            "COMPACT",
            true
        );
        mockMvc
            .perform(
                post(URL + "/{id}/widgets", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(nonTemporalLine))
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
    void persistsLineWidgetsWithTemporalBreakdownsInEitherOrderedPositionWithoutChangingTheirCanonicalAst() throws Exception {
        JsonNode dashboard = create("Comparación ordenada", "create-ordered-comparison", relativeContext());
        long dashboardId = dashboard.path("id").asLong();

        JsonNode categoryThenMonth = addWidget(
            dashboardId,
            dashboard.path("version").asLong(),
            "Categorías por mes",
            "add-category-then-month-line",
            expenseByCategoryAndMonth(false),
            "LINE",
            "FULL",
            true
        );
        long firstWidgetId = categoryThenMonth.path("widgets").get(0).path("id").asLong();
        assertThat(categoryThenMonth.path("widgets").get(0).path("definition").path("groupBy"))
            .extracting(JsonNode::asText)
            .containsExactly("CATEGORY_ROOT", "TRANSACTION_MONTH");
        assertThat(categoryThenMonth.path("widgets").get(0).path("definition").toString()).doesNotContain("context");

        JsonNode monthThenCategory = addWidget(
            dashboardId,
            categoryThenMonth.path("version").asLong(),
            "Meses por categoría",
            "add-month-then-category-line",
            expenseByCategoryAndMonth(true),
            "LINE",
            "FULL",
            true
        );
        long secondWidgetId = monthThenCategory.path("widgets").get(1).path("id").asLong();
        assertThat(monthThenCategory.path("widgets").get(1).path("definition").path("groupBy"))
            .extracting(JsonNode::asText)
            .containsExactly("TRANSACTION_MONTH", "CATEGORY_ROOT");

        ObjectNode groupedBar = widgetUpdatePayload(
            monthThenCategory.path("version").asLong(),
            "Categorías agrupadas por mes",
            expenseByCategoryAndMonth(false),
            "GROUPED_BAR",
            "FULL",
            true
        );
        JsonNode updated = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, firstWidgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(groupedBar))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].id").value(firstWidgetId))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[0].definition.groupBy[0]").value("CATEGORY_ROOT"))
                .andExpect(jsonPath("$.widgets[0].definition.groupBy[1]").value("TRANSACTION_MONTH"))
                .andReturn()
        );
        assertThat(updated.path("widgets").get(1).path("id").asLong()).isEqualTo(secondWidgetId);

        ObjectNode stackedBar = widgetUpdatePayload(
            updated.path("version").asLong(),
            "Meses apilados por categoría",
            expenseByCategoryAndMonth(true),
            "STACKED_BAR",
            "FULL",
            true
        );
        JsonNode withStackedBar = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, secondWidgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(stackedBar))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[1].id").value(secondWidgetId))
                .andExpect(jsonPath("$.widgets[1].definition.groupBy[0]").value("TRANSACTION_MONTH"))
                .andExpect(jsonPath("$.widgets[1].definition.groupBy[1]").value("CATEGORY_ROOT"))
                .andReturn()
        );

        JsonNode reloaded = body(mockMvc.perform(get(URL + "/{id}", dashboardId)).andExpect(status().isOk()).andReturn());
        assertThat(reloaded.path("widgets").get(0).path("id").asLong()).isEqualTo(firstWidgetId);
        assertThat(reloaded.path("widgets").get(0).path("definition").path("groupBy"))
            .extracting(JsonNode::asText)
            .containsExactly("CATEGORY_ROOT", "TRANSACTION_MONTH");
        assertThat(reloaded.path("widgets").get(1).path("definition").path("groupBy"))
            .extracting(JsonNode::asText)
            .containsExactly("TRANSACTION_MONTH", "CATEGORY_ROOT");
        assertThat(reloaded.path("widgets").get(1).path("visualizationType").asText()).isEqualTo("STACKED_BAR");
        assertThat(withStackedBar.path("version").asLong()).isEqualTo(reloaded.path("version").asLong());
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
    void renamesWidgetMetadataWithoutChangingItsCanonicalQueryOrLayout() throws Exception {
        JsonNode dashboard = create("Renombrar widget", "create-rename-widget-1", relativeContext());
        JsonNode withWidget = addWidget(
            dashboard.path("id").asLong(),
            dashboard.path("version").asLong(),
            "Conteo original",
            "add-rename-widget-1",
            countByInstitution(),
            "BAR",
            "FULL",
            false
        );
        JsonNode original = withWidget.path("widgets").get(0);
        long dashboardId = withWidget.path("id").asLong();
        long widgetId = original.path("id").asLong();
        ObjectNode update = widgetUpdatePayload(
            withWidget.path("version").asLong(),
            "Conteo renombrado",
            original.path("definition"),
            original.path("visualizationType").asText(),
            original.path("width").asText(),
            original.path("visible").asBoolean()
        );
        update.put("description", "Una descripción independiente");
        update.set("visualizationOptions", original.path("visualizationOptions"));

        JsonNode renamed = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, widgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(update))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].id").value(widgetId))
                .andExpect(jsonPath("$.widgets[0].title").value("Conteo renombrado"))
                .andExpect(jsonPath("$.widgets[0].description").value("Una descripción independiente"))
                .andReturn()
        );
        JsonNode saved = renamed.path("widgets").get(0);
        assertThat(saved.path("definition")).isEqualTo(original.path("definition"));
        assertThat(saved.path("visualizationType")).isEqualTo(original.path("visualizationType"));
        assertThat(saved.path("visualizationOptions")).isEqualTo(original.path("visualizationOptions"));
        assertThat(saved.path("position")).isEqualTo(original.path("position"));
        assertThat(saved.path("width")).isEqualTo(original.path("width"));
        assertThat(saved.path("visible")).isEqualTo(original.path("visible"));
    }

    @Test
    void duplicatesAWidgetIdempotentlyWithAnIndependentCanonicalDefinitionAndAppendedVisiblePosition() throws Exception {
        JsonNode dashboard = create("Duplicar widget", "create-duplicate-widget-1", relativeContext());
        JsonNode sourceDashboard = addWidget(
            dashboard.path("id").asLong(),
            dashboard.path("version").asLong(),
            "Conteo original",
            "add-duplicate-widget-1",
            countByInstitution(),
            "BAR",
            "FULL",
            false
        );
        long dashboardId = sourceDashboard.path("id").asLong();
        long sourceWidgetId = sourceDashboard.path("widgets").get(0).path("id").asLong();
        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("expectedVersion", sourceDashboard.path("version").asLong());
        duplicate.put("clientRequestId", "duplicate-widget-1");

        JsonNode duplicated = body(
            mockMvc
                .perform(
                    post(URL + "/{dashboardId}/widgets/{widgetId}/duplicate", dashboardId, sourceWidgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(duplicate))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets.length()").value(2))
                .andExpect(jsonPath("$.widgets[0].id").value(sourceWidgetId))
                .andExpect(jsonPath("$.widgets[1].position").value(1))
                .andExpect(jsonPath("$.widgets[1].title").value("Conteo original (copia)"))
                .andExpect(jsonPath("$.widgets[1].width").value("FULL"))
                .andExpect(jsonPath("$.widgets[1].visible").value(true))
                .andReturn()
        );
        long copiedWidgetId = duplicated.path("widgets").get(1).path("id").asLong();
        assertThat(copiedWidgetId).isNotEqualTo(sourceWidgetId);
        assertThat(duplicated.path("widgets").get(1).path("definition")).isEqualTo(
            sourceDashboard.path("widgets").get(0).path("definition")
        );
        assertThat(duplicated.path("widgets").get(1).path("visualizationOptions")).isEqualTo(
            sourceDashboard.path("widgets").get(0).path("visualizationOptions")
        );

        mockMvc
            .perform(
                post(URL + "/{dashboardId}/widgets/{widgetId}/duplicate", dashboardId, sourceWidgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicate))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.widgets.length()").value(2))
            .andExpect(jsonPath("$.widgets[1].id").value(copiedWidgetId));

        ObjectNode updateCopy = widgetUpdatePayload(
            duplicated.path("version").asLong(),
            "Conteo independiente",
            expenseTotal(),
            "KPI",
            "FULL",
            true
        );
        JsonNode updated = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, copiedWidgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateCopy))
                )
                .andExpect(status().isOk())
                .andReturn()
        );
        assertThat(updated.path("widgets").get(0).path("id").asLong()).isEqualTo(sourceWidgetId);
        assertThat(updated.path("widgets").get(0).path("title").asText()).isEqualTo("Conteo original");
        assertThat(updated.path("widgets").get(0).path("definition")).isEqualTo(sourceDashboard.path("widgets").get(0).path("definition"));
        assertThat(updated.path("widgets").get(1).path("title").asText()).isEqualTo("Conteo independiente");
    }

    @Test
    void rejectsWidgetDuplicationWhenTheDashboardAlreadyHasSixteenWidgets() throws Exception {
        JsonNode dashboard = create("Límite de widgets", "create-widget-limit-1", relativeContext());
        JsonNode current = dashboard;
        for (int index = 0; index < 16; index++) {
            current = addWidget(
                dashboard.path("id").asLong(),
                current.path("version").asLong(),
                "Widget " + index,
                "add-widget-limit-" + index,
                expenseTotal(),
                "KPI",
                "COMPACT",
                true
            );
        }
        long sourceWidgetId = current.path("widgets").get(0).path("id").asLong();
        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("expectedVersion", current.path("version").asLong());
        duplicate.put("clientRequestId", "duplicate-widget-limit-1");

        mockMvc
            .perform(
                post(URL + "/{dashboardId}/widgets/{widgetId}/duplicate", dashboard.path("id").asLong(), sourceWidgetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicate))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
    }

    @Test
    void deletesOnlyTheSelectedWidgetCompactsPositionsAndRejectsStaleMutations() throws Exception {
        JsonNode dashboard = create("Eliminar widget", "create-delete-widget-1", relativeContext());
        long dashboardId = dashboard.path("id").asLong();
        JsonNode first = addWidget(
            dashboardId,
            dashboard.path("version").asLong(),
            "Primero",
            "add-delete-widget-1",
            expenseTotal(),
            "KPI",
            "COMPACT",
            true
        );
        long firstWidgetId = first.path("widgets").get(0).path("id").asLong();
        JsonNode second = addWidget(
            dashboardId,
            first.path("version").asLong(),
            "Segundo",
            "add-delete-widget-2",
            expenseTotal(),
            "KPI",
            "COMPACT",
            true
        );
        long secondWidgetId = second.path("widgets").get(1).path("id").asLong();
        JsonNode third = addWidget(
            dashboardId,
            second.path("version").asLong(),
            "Tercero",
            "add-delete-widget-3",
            expenseTotal(),
            "KPI",
            "COMPACT",
            true
        );
        long thirdWidgetId = third.path("widgets").get(2).path("id").asLong();

        JsonNode deleted = body(
            mockMvc
                .perform(
                    delete(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, secondWidgetId).param(
                        "expectedVersion",
                        third.path("version").asText()
                    )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets.length()").value(2))
                .andExpect(jsonPath("$.widgets[0].id").value(firstWidgetId))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[1].id").value(thirdWidgetId))
                .andExpect(jsonPath("$.widgets[1].position").value(1))
                .andReturn()
        );
        mockMvc
            .perform(
                delete(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, firstWidgetId).param(
                    "expectedVersion",
                    third.path("version").asText()
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mockMvc
            .perform(
                delete(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, secondWidgetId).param(
                    "expectedVersion",
                    deleted.path("version").asText()
                )
            )
            .andExpect(status().isNotFound());

        JsonNode lastDeleted = body(
            mockMvc
                .perform(
                    delete(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, thirdWidgetId).param(
                        "expectedVersion",
                        deleted.path("version").asText()
                    )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets.length()").value(1))
                .andExpect(jsonPath("$.widgets[0].id").value(firstWidgetId))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andReturn()
        );
        mockMvc
            .perform(
                delete(URL + "/{dashboardId}/widgets/{widgetId}", dashboardId, firstWidgetId).param(
                    "expectedVersion",
                    lastDeleted.path("version").asText()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.widgets.length()").value(0));
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

        JsonNode editedCopy = body(
            mockMvc
                .perform(
                    put(URL + "/{dashboardId}/widgets/{widgetId}", copiedId, firstWidgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsBytes(
                                widgetUpdatePayload(
                                    created.path("version").asLong(),
                                    "Conteo personalizado",
                                    countByInstitution(),
                                    "BAR",
                                    "MEDIUM",
                                    true
                                )
                            )
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.widgets[0].id").value(firstWidgetId))
                .andExpect(jsonPath("$.widgets[0].title").value("Conteo personalizado"))
                .andReturn()
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
        assertThat(second.path("widgets").get(0).path("title").asText()).isEqualTo("Conteo por institución");
        assertThat(editedCopy.path("widgets").get(0).path("title").asText()).isEqualTo("Conteo personalizado");

        ObjectNode rejected = templateCopyPayload("financial-overview", "No parcial", "copy-overview-invalid");
        ((ObjectNode) rejected.withArray("widgets").get(0)).put("visualizationType", "KPI");
        mockMvc
            .perform(post(URL + "/from-template").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(rejected)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(2);
    }

    @Test
    void updatesOnlyMetadataWithNormalizedNameValidationAndOptimisticConcurrency() throws Exception {
        JsonNode dashboard = create("Flujo mensual", "metadata-create-1", relativeContext());
        long dashboardId = dashboard.path("id").asLong();
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("expectedVersion", dashboard.path("version").asLong());
        metadata.put("name", "  Flujo   actualizado  ");
        metadata.put("description", "Una descripción actualizada");

        JsonNode updated = body(
            mockMvc
                .perform(
                    patch(URL + "/{id}/metadata", dashboardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(metadata))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Flujo actualizado"))
                .andExpect(jsonPath("$.description").value("Una descripción actualizada"))
                .andExpect(jsonPath("$.defaultContext.time.relativePeriod").value("THIS_MONTH"))
                .andReturn()
        );

        mockMvc
            .perform(
                post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(" flujo actualizado ", "metadata-create-duplicate", relativeContext()))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        mockMvc
            .perform(
                patch(URL + "/{id}/metadata", dashboardId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(metadata))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        assertThat(updated.path("widgets")).isEmpty();
    }

    @Test
    void duplicatesAnOwnedDashboardAtomicallyWithIndependentWidgetsAndAnIdempotentReceipt() throws Exception {
        JsonNode source = create("Origen", "duplicate-create-1", relativeContext());
        long sourceId = source.path("id").asLong();
        JsonNode withFirstWidget = addWidget(
            sourceId,
            source.path("version").asLong(),
            "Conteo por institución",
            "duplicate-widget-1",
            countByInstitution(),
            "BAR",
            "FULL",
            false
        );
        JsonNode sourceWithWidgets = addWidget(
            sourceId,
            withFirstWidget.path("version").asLong(),
            "Gastos",
            "duplicate-widget-2",
            expenseTotal(),
            "KPI",
            "COMPACT",
            true
        );
        long sourceFirstWidgetId = sourceWithWidgets.path("widgets").get(0).path("id").asLong();
        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("expectedVersion", sourceWithWidgets.path("version").asLong());
        duplicate.put("name", "Copia independiente");
        duplicate.put("description", "Conservar definición y distribución");
        duplicate.put("clientRequestId", "duplicate-source-1");

        JsonNode copied = body(
            mockMvc
                .perform(
                    post(URL + "/{id}/duplicate", sourceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(duplicate))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Copia independiente"))
                .andExpect(jsonPath("$.widgets.length()").value(2))
                .andExpect(jsonPath("$.widgets[0].position").value(0))
                .andExpect(jsonPath("$.widgets[0].width").value("FULL"))
                .andExpect(jsonPath("$.widgets[0].visible").value(false))
                .andExpect(jsonPath("$.widgets[1].position").value(1))
                .andExpect(jsonPath("$.widgets[1].width").value("COMPACT"))
                .andExpect(jsonPath("$.widgets[1].visible").value(true))
                .andReturn()
        );
        long copiedId = copied.path("id").asLong();
        long copiedFirstWidgetId = copied.path("widgets").get(0).path("id").asLong();
        assertThat(copiedId).isNotEqualTo(sourceId);
        assertThat(copiedFirstWidgetId).isNotEqualTo(sourceFirstWidgetId);
        assertThat(copied.path("widgets").get(0).path("definition")).isEqualTo(sourceWithWidgets.path("widgets").get(0).path("definition"));
        assertThat(copied.path("defaultContext")).isEqualTo(sourceWithWidgets.path("defaultContext"));

        mockMvc
            .perform(
                post(URL + "/{id}/duplicate", sourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicate))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(copiedId));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(2);

        ObjectNode copiedMetadata = objectMapper.createObjectNode();
        copiedMetadata.put("expectedVersion", copied.path("version").asLong());
        copiedMetadata.put("name", "Copia modificada");
        mockMvc
            .perform(
                patch(URL + "/{id}/metadata", copiedId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(copiedMetadata))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Copia modificada"));
        mockMvc.perform(get(URL + "/{id}", sourceId)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Origen"));

        ObjectNode rejected = duplicate.deepCopy();
        rejected.put("name", "Origen");
        rejected.put("clientRequestId", "duplicate-source-conflict");
        mockMvc
            .perform(
                post(URL + "/{id}/duplicate", sourceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(rejected))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(2);
    }

    @Test
    void rejectsDuplicationAtTheOwnerDashboardLimitWithoutCreatingAPartialCopy() throws Exception {
        JsonNode source = create("Límite 0", "duplicate-limit-create-0", relativeContext());
        for (int index = 1; index < 20; index++) {
            create("Límite " + index, "duplicate-limit-create-" + index, relativeContext());
        }
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(20);

        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("expectedVersion", source.path("version").asLong());
        duplicate.put("name", "No debe crearse");
        duplicate.put("clientRequestId", "duplicate-limit-rejected");
        mockMvc
            .perform(
                post(URL + "/{id}/duplicate", source.path("id").asLong())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicate))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));
        assertThat(dashboardRepository.countByUserLogin("user")).isEqualTo(20);
    }

    @Test
    void persistsOneOfficialOrOwnedPersonalDefaultAndFallsBackAfterDeletion() throws Exception {
        JsonNode dashboard = create("Predeterminado", "default-create-1", relativeContext());
        long dashboardId = dashboard.path("id").asLong();

        mockMvc
            .perform(get(URL + "/default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dashboardId").isEmpty())
            .andExpect(jsonPath("$.officialTemplateId").value("financial-overview"))
            .andExpect(jsonPath("$.version").isEmpty());

        ObjectNode selectMovement = objectMapper.createObjectNode().put("officialTemplateId", "movement-analysis");
        JsonNode movementSelected = body(
            mockMvc
                .perform(
                    put(URL + "/default").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(selectMovement))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboardId").isEmpty())
                .andExpect(jsonPath("$.officialTemplateId").value("movement-analysis"))
                .andReturn()
        );
        long movementVersion = movementSelected.path("version").asLong();
        mockMvc
            .perform(get(URL + "/default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.officialTemplateId").value("movement-analysis"));

        ObjectNode selectAccounts = objectMapper
            .createObjectNode()
            .put("expectedVersion", movementVersion)
            .put("officialTemplateId", "accounts-and-position");
        JsonNode accountsSelected = body(
            mockMvc
                .perform(
                    put(URL + "/default").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(selectAccounts))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboardId").isEmpty())
                .andExpect(jsonPath("$.officialTemplateId").value("accounts-and-position"))
                .andReturn()
        );

        ObjectNode selectPersonal = objectMapper
            .createObjectNode()
            .put("expectedVersion", accountsSelected.path("version").asLong())
            .put("dashboardId", dashboardId);
        JsonNode personalSelected = body(
            mockMvc
                .perform(
                    put(URL + "/default").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(selectPersonal))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboardId").value(dashboardId))
                .andExpect(jsonPath("$.officialTemplateId").isEmpty())
                .andReturn()
        );
        long preferenceVersion = personalSelected.path("version").asLong();

        ObjectNode stale = objectMapper.createObjectNode().put("officialTemplateId", "financial-overview");
        mockMvc
            .perform(put(URL + "/default").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(stale)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        ObjectNode invalidCombinedSelection = objectMapper
            .createObjectNode()
            .put("expectedVersion", preferenceVersion)
            .put("dashboardId", dashboardId)
            .put("officialTemplateId", "financial-overview");
        mockMvc
            .perform(
                put(URL + "/default")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(invalidCombinedSelection))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));

        mockMvc
            .perform(delete(URL + "/{id}", dashboardId).param("expectedVersion", dashboard.path("version").asText()))
            .andExpect(status().isNoContent());
        mockMvc
            .perform(get(URL + "/default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dashboardId").isEmpty())
            .andExpect(jsonPath("$.officialTemplateId").value("financial-overview"))
            .andExpect(jsonPath("$.version").value(preferenceVersion + 1));
    }

    @Test
    void preventsCrossOwnerDuplicationAndDefaultSelection() throws Exception {
        User other = UserResourceIT.createEntity();
        entityManager.persist(other);
        PersonalDashboard foreign = new PersonalDashboard();
        foreign.setUser(other);
        foreign.setName("Privado ajeno");
        foreign.setNormalizedName("privado ajeno");
        foreign.setDefaultContextJson(objectMapper.writeValueAsString(relativeContext()));
        foreign.setSchemaVersion(1);
        foreign.setCreatedAt(Instant.now());
        foreign.setUpdatedAt(Instant.now());
        foreign = dashboardRepository.saveAndFlush(foreign);

        ObjectNode duplicate = objectMapper.createObjectNode();
        duplicate.put("expectedVersion", foreign.getVersion());
        duplicate.put("name", "No autorizado");
        duplicate.put("clientRequestId", "duplicate-foreign-1");
        mockMvc
            .perform(
                post(URL + "/{id}/duplicate", foreign.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(duplicate))
            )
            .andExpect(status().isNotFound());

        ObjectNode widgetDuplicate = objectMapper.createObjectNode();
        widgetDuplicate.put("expectedVersion", foreign.getVersion());
        widgetDuplicate.put("clientRequestId", "duplicate-foreign-widget-1");
        mockMvc
            .perform(
                post(URL + "/{dashboardId}/widgets/{widgetId}/duplicate", foreign.getId(), 999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(widgetDuplicate))
            )
            .andExpect(status().isNotFound());
        mockMvc
            .perform(
                delete(URL + "/{dashboardId}/widgets/{widgetId}", foreign.getId(), 999L).param(
                    "expectedVersion",
                    foreign.getVersion().toString()
                )
            )
            .andExpect(status().isNotFound());

        ObjectNode select = objectMapper.createObjectNode().put("dashboardId", foreign.getId());
        mockMvc
            .perform(put(URL + "/default").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(select)))
            .andExpect(status().isNotFound());
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

    private ObjectNode expenseByCategoryAndMonth(boolean monthFirst) {
        ObjectNode definition = expenseTotal();
        ArrayNode groupBy = definition.withArray("groupBy");
        ArrayNode orderBy = definition.withArray("orderBy");
        if (monthFirst) {
            groupBy.add("TRANSACTION_MONTH").add("CATEGORY_ROOT");
            orderBy.addObject().put("field", "TRANSACTION_MONTH").put("direction", "ASC");
            orderBy.addObject().put("field", "CATEGORY_ROOT").put("direction", "ASC");
        } else {
            groupBy.add("CATEGORY_ROOT").add("TRANSACTION_MONTH");
            orderBy.addObject().put("field", "CATEGORY_ROOT").put("direction", "ASC");
            orderBy.addObject().put("field", "TRANSACTION_MONTH").put("direction", "ASC");
        }
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
