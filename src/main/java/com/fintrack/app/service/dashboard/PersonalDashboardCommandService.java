package com.fintrack.app.service.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fintrack.app.domain.PersonalDashboard;
import com.fintrack.app.domain.PersonalDashboardPreference;
import com.fintrack.app.domain.PersonalDashboardRequest;
import com.fintrack.app.domain.PersonalDashboardWidget;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import com.fintrack.app.repository.PersonalDashboardPreferenceRepository;
import com.fintrack.app.repository.PersonalDashboardRepository;
import com.fintrack.app.repository.PersonalDashboardRequestRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.analytics.SemanticAnalyticsSchema;
import com.fintrack.app.service.analytics.SemanticAnalyticsValidationException;
import com.fintrack.app.service.analytics.SemanticAnalyticsValidator;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import com.fintrack.app.service.analytics.SemanticVisualizationCompatibility;
import com.fintrack.app.service.analytics.VisualizationContext;
import com.fintrack.app.service.analytics.VisualizationTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole write boundary for user-owned dashboards. Every mutation loads the aggregate in the authenticated
 * owner's scope and changes its timestamp so the root @Version covers metadata, widgets, and layout alike.
 */
@Service
@Transactional
public class PersonalDashboardCommandService {

    static final int MAX_DASHBOARDS_PER_USER = 20;
    static final int MAX_WIDGETS_PER_DASHBOARD = 16;
    static final int MAX_QUERY_JSON_BYTES = 32 * 1024;
    static final int MAX_VISUALIZATION_OPTIONS_BYTES = 8 * 1024;
    private static final int MAX_JSON_DEPTH = 12;
    private static final String CREATE_SCOPE = "CREATE_DASHBOARD";
    private static final String COPY_TEMPLATE_SCOPE = "COPY_OFFICIAL_TEMPLATE";
    private static final String ADD_WIDGET_SCOPE = "ADD_WIDGET";
    private static final String DUPLICATE_WIDGET_SCOPE = "DUPLICATE_WIDGET";
    private static final String DUPLICATE_DASHBOARD_SCOPE = "DUPLICATE_DASHBOARD";
    static final String FINANCIAL_OVERVIEW_TEMPLATE_ID = "financial-overview";
    private static final Set<String> OFFICIAL_TEMPLATE_IDS = Set.of(
        FINANCIAL_OVERVIEW_TEMPLATE_ID,
        "movement-analysis",
        "accounts-and-position"
    );

    private final PersonalDashboardRepository dashboardRepository;
    private final PersonalDashboardPreferenceRepository preferenceRepository;
    private final PersonalDashboardRequestRepository requestRepository;
    private final CurrentUserService currentUserService;
    private final SemanticAnalyticsValidator semanticAnalyticsValidator;
    private final ObjectMapper objectMapper;

    public PersonalDashboardCommandService(
        PersonalDashboardRepository dashboardRepository,
        PersonalDashboardPreferenceRepository preferenceRepository,
        PersonalDashboardRequestRepository requestRepository,
        CurrentUserService currentUserService,
        SemanticAnalyticsValidator semanticAnalyticsValidator,
        ObjectMapper objectMapper
    ) {
        this.dashboardRepository = dashboardRepository;
        this.preferenceRepository = preferenceRepository;
        this.requestRepository = requestRepository;
        this.currentUserService = currentUserService;
        this.semanticAnalyticsValidator = semanticAnalyticsValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PersonalDashboardSummary> listOwn() {
        return dashboardRepository.findAllByUserLogin(currentUserService.getCurrentUserLogin()).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PersonalDashboardDetail> findOwn(Long dashboardId) {
        return dashboardRepository
            .findOneWithWidgetsByIdAndUserLogin(dashboardId, currentUserService.getCurrentUserLogin())
            .map(this::detail);
    }

    public PersonalDashboardDetail create(PersonalDashboardCreateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A dashboard command is required.");
        User owner = currentUserService.getCurrentUser();
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(command);
        Optional<PersonalDashboardRequest> existing = existingRequest(owner, CREATE_SCOPE, requestId, digest);
        if (existing.isPresent()) return replayDashboard(existing.orElseThrow(), owner);
        if (dashboardRepository.countByUserLogin(owner.getLogin()) >= MAX_DASHBOARDS_PER_USER) {
            throw failure(PersonalDashboardCommandException.Code.LIMIT_EXCEEDED, "A user may keep at most 20 personal dashboards.");
        }
        String name = name(command.name());
        String normalizedName = normalizedName(name);
        if (dashboardRepository.existsByUserLoginAndNormalizedName(owner.getLogin(), normalizedName)) {
            throw failure(PersonalDashboardCommandException.Code.DUPLICATE_NAME, "A personal dashboard with that name already exists.");
        }
        VisualizationContext context = normalizeContext(command.defaultContext());
        Instant now = Instant.now();
        PersonalDashboard dashboard = new PersonalDashboard();
        dashboard.setUser(owner);
        dashboard.setName(name);
        dashboard.setNormalizedName(normalizedName);
        dashboard.setDescription(description(command.description()));
        dashboard.setDefaultContextJson(write(context, MAX_QUERY_JSON_BYTES, "Dashboard context is too large."));
        dashboard.setSchemaVersion(SemanticAnalyticsSchema.VERSION);
        dashboard.setCreatedAt(now);
        dashboard.setUpdatedAt(now);
        dashboard = dashboardRepository.saveAndFlush(dashboard);
        saveRequest(owner, CREATE_SCOPE, requestId, digest, dashboard.getId(), null);
        return detail(dashboard);
    }

    /**
     * Persists a complete independent copy in one transaction. A front end builds its payload directly from the one
     * official template registry; this boundary verifies the controlled template key and validates every canonical
     * widget rather than granting provenance or ownership based on client input.
     */
    public PersonalDashboardDetail copyOfficialTemplate(PersonalDashboardTemplateCopyCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A template copy command is required.");
        require(
            command.templateId() != null && OFFICIAL_TEMPLATE_IDS.contains(command.templateId()),
            PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
            "An implemented official template is required."
        );
        require(
            command.widgets() != null && !command.widgets().isEmpty() && command.widgets().size() <= MAX_WIDGETS_PER_DASHBOARD,
            PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
            "An official template must contain between 1 and 16 widgets."
        );
        User owner = currentUserService.getCurrentUser();
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(command);
        Optional<PersonalDashboardRequest> existing = existingRequest(owner, COPY_TEMPLATE_SCOPE, requestId, digest);
        if (existing.isPresent()) return replayDashboard(existing.orElseThrow(), owner);
        if (dashboardRepository.countByUserLogin(owner.getLogin()) >= MAX_DASHBOARDS_PER_USER) {
            throw failure(PersonalDashboardCommandException.Code.LIMIT_EXCEEDED, "A user may keep at most 20 personal dashboards.");
        }
        String name = name(command.name());
        String normalizedName = normalizedName(name);
        if (dashboardRepository.existsByUserLoginAndNormalizedName(owner.getLogin(), normalizedName)) {
            throw failure(PersonalDashboardCommandException.Code.DUPLICATE_NAME, "A personal dashboard with that name already exists.");
        }
        VisualizationContext context = normalizeContext(command.defaultContext());
        Instant now = Instant.now();
        PersonalDashboard dashboard = new PersonalDashboard();
        dashboard.setUser(owner);
        dashboard.setName(name);
        dashboard.setNormalizedName(normalizedName);
        dashboard.setDescription(description(command.description()));
        dashboard.setDefaultContextJson(write(context, MAX_QUERY_JSON_BYTES, "Dashboard context is too large."));
        dashboard.setSchemaVersion(SemanticAnalyticsSchema.VERSION);
        dashboard.setCreatedAt(now);
        dashboard.setUpdatedAt(now);

        for (int position = 0; position < command.widgets().size(); position++) {
            PersonalDashboardTemplateCopyCommand.TemplateWidget source = command.widgets().get(position);
            require(source != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "Every template widget is required.");
            ValidatedWidget payload = validateWidget(
                dashboard,
                source.title(),
                source.description(),
                source.definition(),
                source.visualizationType(),
                source.visualizationOptions(),
                source.width(),
                source.visible()
            );
            PersonalDashboardWidget widget = new PersonalDashboardWidget();
            apply(widget, payload, now);
            widget.setPosition(position);
            dashboard.addWidget(widget);
        }
        PersonalDashboard saved = dashboardRepository.saveAndFlush(dashboard);
        saveRequest(owner, COPY_TEMPLATE_SCOPE, requestId, digest, saved.getId(), null);
        return detail(saved);
    }

    public PersonalDashboardDetail update(Long dashboardId, PersonalDashboardUpdateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A dashboard update is required.");
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        String name = name(command.name());
        String normalizedName = normalizedName(name);
        if (
            !normalizedName.equals(dashboard.getNormalizedName()) &&
            dashboardRepository.existsByUserLoginAndNormalizedName(currentUserService.getCurrentUserLogin(), normalizedName)
        ) {
            throw failure(PersonalDashboardCommandException.Code.DUPLICATE_NAME, "A personal dashboard with that name already exists.");
        }
        dashboard.setName(name);
        dashboard.setNormalizedName(normalizedName);
        dashboard.setDescription(description(command.description()));
        dashboard.setDefaultContextJson(
            write(normalizeContext(command.defaultContext()), MAX_QUERY_JSON_BYTES, "Dashboard context is too large.")
        );
        touch(dashboard);
        return detail(dashboardRepository.saveAndFlush(dashboard));
    }

    /** Changes only user-facing metadata, so it cannot alter a saved context, widget, or layout. */
    public PersonalDashboardDetail updateMetadata(Long dashboardId, PersonalDashboardMetadataUpdateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A dashboard metadata command is required.");
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        String name = name(command.name());
        String normalizedName = normalizedName(name);
        if (
            !normalizedName.equals(dashboard.getNormalizedName()) &&
            dashboardRepository.existsByUserLoginAndNormalizedName(currentUserService.getCurrentUserLogin(), normalizedName)
        ) {
            throw failure(PersonalDashboardCommandException.Code.DUPLICATE_NAME, "A personal dashboard with that name already exists.");
        }
        dashboard.setName(name);
        dashboard.setNormalizedName(normalizedName);
        dashboard.setDescription(description(command.description()));
        touch(dashboard);
        return detail(dashboardRepository.saveAndFlush(dashboard));
    }

    /**
     * Copies only durable dashboard definition state. Results and browser-only selections never enter the aggregate,
     * so they cannot be copied by this command.
     */
    public PersonalDashboardDetail duplicate(Long sourceDashboardId, PersonalDashboardDuplicateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A dashboard duplication command is required.");
        User owner = currentUserService.getCurrentUser();
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(new DuplicateRequestFingerprint(sourceDashboardId, command));
        Optional<PersonalDashboardRequest> existing = existingRequest(owner, DUPLICATE_DASHBOARD_SCOPE, requestId, digest);
        if (existing.isPresent()) return replayDashboard(existing.orElseThrow(), owner);

        PersonalDashboard source = ownedDashboard(sourceDashboardId);
        requireExpectedVersion(source, command.expectedVersion());
        if (dashboardRepository.countByUserLogin(owner.getLogin()) >= MAX_DASHBOARDS_PER_USER) {
            throw failure(PersonalDashboardCommandException.Code.LIMIT_EXCEEDED, "A user may keep at most 20 personal dashboards.");
        }
        String name = name(command.name());
        String normalizedName = normalizedName(name);
        if (dashboardRepository.existsByUserLoginAndNormalizedName(owner.getLogin(), normalizedName)) {
            throw failure(PersonalDashboardCommandException.Code.DUPLICATE_NAME, "A personal dashboard with that name already exists.");
        }

        Instant now = Instant.now();
        PersonalDashboard duplicate = new PersonalDashboard();
        duplicate.setUser(owner);
        duplicate.setName(name);
        duplicate.setNormalizedName(normalizedName);
        duplicate.setDescription(description(command.description()));
        duplicate.setDefaultContextJson(source.getDefaultContextJson());
        duplicate.setSchemaVersion(source.getSchemaVersion());
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);
        source
            .getWidgets()
            .stream()
            .sorted(Comparator.comparing(PersonalDashboardWidget::getPosition).thenComparing(PersonalDashboardWidget::getId))
            .forEach(sourceWidget -> duplicate.addWidget(copyWidget(sourceWidget, now)));

        PersonalDashboard saved = dashboardRepository.saveAndFlush(duplicate);
        saveRequest(owner, DUPLICATE_DASHBOARD_SCOPE, requestId, digest, saved.getId(), null);
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public PersonalDashboardDefaultView defaultOwn() {
        User owner = currentUserService.getCurrentUser();
        return preferenceView(preferenceRepository.findByUserId(owner.getId()).orElse(null), owner);
    }

    /** Stores one owner-scoped selection, without materializing or copying official dashboards. */
    public PersonalDashboardDefaultView setDefault(PersonalDashboardDefaultCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A default dashboard command is required.");
        User owner = currentUserService.getCurrentUser();
        PersonalDashboardPreference preference = preferenceRepository.findByUserId(owner.getId()).orElse(null);
        if (preference == null) {
            require(
                command.expectedVersion() == null,
                PersonalDashboardCommandException.Code.VERSION_CONFLICT,
                "The default dashboard preference was updated elsewhere. Reload it before saving."
            );
            preference = new PersonalDashboardPreference();
            preference.setUser(owner);
            preference.setCreatedAt(Instant.now());
        } else if (!Objects.equals(preference.getVersion(), command.expectedVersion())) {
            throw failure(
                PersonalDashboardCommandException.Code.VERSION_CONFLICT,
                "The default dashboard preference was updated elsewhere. Reload it before saving."
            );
        }
        boolean selectsPersonalDashboard = command.dashboardId() != null;
        String officialTemplateId = command.officialTemplateId();
        // The original endpoint used a null personal ID for the Financial Overview. Keep that safe wire behavior
        // while normalizing every returned preference to an explicit official template identifier.
        if (!selectsPersonalDashboard && officialTemplateId == null) officialTemplateId = FINANCIAL_OVERVIEW_TEMPLATE_ID;
        require(
            !(selectsPersonalDashboard && officialTemplateId != null),
            PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
            "Select either one personal dashboard or one official dashboard."
        );
        if (officialTemplateId != null) {
            require(
                OFFICIAL_TEMPLATE_IDS.contains(officialTemplateId),
                PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
                "An implemented official dashboard is required."
            );
            preference.setDefaultDashboard(null);
            preference.setDefaultOfficialTemplateId(officialTemplateId);
        } else {
            preference.setDefaultDashboard(ownedDashboard(command.dashboardId()));
            preference.setDefaultOfficialTemplateId(null);
        }
        preference.setUpdatedAt(Instant.now());
        return preferenceView(preferenceRepository.saveAndFlush(preference), owner);
    }

    public PersonalDashboardDetail addWidget(Long dashboardId, PersonalDashboardWidgetAddCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A widget command is required.");
        User owner = currentUserService.getCurrentUser();
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(command);
        Optional<PersonalDashboardRequest> existing = existingRequest(owner, ADD_WIDGET_SCOPE, requestId, digest);
        if (existing.isPresent()) return replayWidgetDashboard(existing.orElseThrow(), owner);
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        if (dashboard.getWidgets().size() >= MAX_WIDGETS_PER_DASHBOARD) {
            throw failure(PersonalDashboardCommandException.Code.LIMIT_EXCEEDED, "A personal dashboard may contain at most 16 widgets.");
        }
        ValidatedWidget payload = validateWidget(
            dashboard,
            command.title(),
            command.description(),
            command.definition(),
            command.visualizationType(),
            command.visualizationOptions(),
            command.width(),
            command.visible()
        );
        Instant now = Instant.now();
        PersonalDashboardWidget widget = new PersonalDashboardWidget();
        apply(widget, payload, now);
        widget.setPosition(nextPosition(dashboard));
        dashboard.addWidget(widget);
        touch(dashboard);
        PersonalDashboard saved = dashboardRepository.saveAndFlush(dashboard);
        Long widgetId = saved.getWidgets().stream().max(Comparator.comparing(PersonalDashboardWidget::getPosition)).orElseThrow().getId();
        saveRequest(owner, ADD_WIDGET_SCOPE, requestId, digest, saved.getId(), widgetId);
        return detail(saved);
    }

    public PersonalDashboardDetail updateWidget(Long dashboardId, Long widgetId, PersonalDashboardWidgetUpdateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A widget update is required.");
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        PersonalDashboardWidget widget = ownedWidget(dashboard, widgetId);
        ValidatedWidget payload = validateWidget(
            dashboard,
            command.title(),
            command.description(),
            command.definition(),
            command.visualizationType(),
            command.visualizationOptions(),
            command.width(),
            command.visible()
        );
        apply(widget, payload, widget.getCreatedAt());
        widget.setUpdatedAt(Instant.now());
        touch(dashboard);
        return detail(dashboardRepository.saveAndFlush(dashboard));
    }

    /**
     * Creates a durable copy without accepting a client-supplied query or presentation payload. Browser-only state
     * such as temporary intervals and hidden legend series therefore cannot enter the persisted copy.
     */
    public PersonalDashboardDetail duplicateWidget(Long dashboardId, Long widgetId, PersonalDashboardWidgetDuplicateCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A widget duplication command is required.");
        User owner = currentUserService.getCurrentUser();
        String requestId = clientRequestId(command.clientRequestId());
        String digest = digest(new DuplicateWidgetRequestFingerprint(dashboardId, widgetId, command));
        Optional<PersonalDashboardRequest> existing = existingRequest(owner, DUPLICATE_WIDGET_SCOPE, requestId, digest);
        if (existing.isPresent()) return replayWidgetDashboard(existing.orElseThrow(), owner);

        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        if (dashboard.getWidgets().size() >= MAX_WIDGETS_PER_DASHBOARD) {
            throw failure(PersonalDashboardCommandException.Code.LIMIT_EXCEEDED, "A personal dashboard may contain at most 16 widgets.");
        }
        PersonalDashboardWidget source = ownedWidget(dashboard, widgetId);
        Instant now = Instant.now();
        PersonalDashboardWidget duplicate = copyWidget(source, now);
        duplicate.setTitle(copyTitle(source.getTitle()));
        duplicate.setPosition(nextPosition(dashboard));
        duplicate.setVisible(true);
        dashboard.addWidget(duplicate);
        touch(dashboard);
        PersonalDashboard saved = dashboardRepository.saveAndFlush(dashboard);
        Long duplicateId = saved
            .getWidgets()
            .stream()
            .max(Comparator.comparing(PersonalDashboardWidget::getPosition).thenComparing(PersonalDashboardWidget::getId))
            .orElseThrow()
            .getId();
        saveRequest(owner, DUPLICATE_WIDGET_SCOPE, requestId, digest, saved.getId(), duplicateId);
        return detail(saved);
    }

    public PersonalDashboardDetail removeWidget(Long dashboardId, Long widgetId, Long expectedVersion) {
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, expectedVersion);
        dashboard.removeWidget(ownedWidget(dashboard, widgetId));
        normalizeWidgetPositions(dashboard);
        touch(dashboard);
        return detail(dashboardRepository.saveAndFlush(dashboard));
    }

    /** Reorders the complete collection and applies width/visibility in one aggregate mutation. */
    public PersonalDashboardDetail saveLayout(Long dashboardId, PersonalDashboardLayoutCommand command) {
        require(command != null, PersonalDashboardCommandException.Code.INVALID_LAYOUT, "A complete layout is required.");
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, command.expectedVersion());
        Map<Long, PersonalDashboardLayoutCommand.WidgetLayoutItem> requested = layoutItems(command.widgets());
        if (requested.size() != dashboard.getWidgets().size() || !requested.keySet().equals(widgetIds(dashboard))) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_LAYOUT, "Layout must contain every widget exactly once.");
        }
        dashboard.getWidgets().forEach(widget -> validateWidgetWidth(widget.getVisualizationType(), requested.get(widget.getId()).width()));
        int temporaryStart =
            dashboard.getWidgets().stream().mapToInt(PersonalDashboardWidget::getPosition).max().orElse(-1) +
            dashboard.getWidgets().size() +
            1;
        for (int index = 0; index < command.widgets().size(); index++) {
            Long widgetId = command.widgets().get(index).widgetId();
            dashboard
                .getWidgets()
                .stream()
                .filter(widget -> widget.getId().equals(widgetId))
                .findFirst()
                .orElseThrow()
                .setPosition(temporaryStart + index);
        }
        dashboardRepository.flush(); // avoids transient unique (dashboard_id, position) collisions while swapping positions.
        for (int index = 0; index < command.widgets().size(); index++) {
            PersonalDashboardLayoutCommand.WidgetLayoutItem item = command.widgets().get(index);
            PersonalDashboardWidget widget = dashboard
                .getWidgets()
                .stream()
                .filter(candidate -> candidate.getId().equals(item.widgetId()))
                .findFirst()
                .orElseThrow();
            widget.setPosition(index);
            widget.setWidth(item.width());
            widget.setVisible(item.visible());
            widget.setUpdatedAt(Instant.now());
        }
        touch(dashboard);
        return detail(dashboardRepository.saveAndFlush(dashboard));
    }

    public void delete(Long dashboardId, Long expectedVersion) {
        PersonalDashboard dashboard = ownedDashboard(dashboardId);
        requireExpectedVersion(dashboard, expectedVersion);
        User owner = currentUserService.getCurrentUser();
        preferenceRepository
            .findByUserId(owner.getId())
            .filter(preference ->
                Objects.equals(
                    preference.getDefaultDashboard() == null ? null : preference.getDefaultDashboard().getId(),
                    dashboard.getId()
                )
            )
            .ifPresent(preference -> {
                preference.setDefaultDashboard(null);
                preference.setDefaultOfficialTemplateId(FINANCIAL_OVERVIEW_TEMPLATE_ID);
                preference.setUpdatedAt(Instant.now());
                preferenceRepository.saveAndFlush(preference);
            });
        dashboardRepository.delete(dashboard);
        dashboardRepository.flush();
    }

    private PersonalDashboard ownedDashboard(Long dashboardId) {
        if (dashboardId == null || dashboardId <= 0) throw failure(
            PersonalDashboardCommandException.Code.NOT_FOUND,
            "Personal dashboard was not found."
        );
        return dashboardRepository
            .findOneWithWidgetsByIdAndUserLogin(dashboardId, currentUserService.getCurrentUserLogin())
            .orElseThrow(() -> failure(PersonalDashboardCommandException.Code.NOT_FOUND, "Personal dashboard was not found."));
    }

    private PersonalDashboardWidget ownedWidget(PersonalDashboard dashboard, Long widgetId) {
        return dashboard
            .getWidgets()
            .stream()
            .filter(widget -> Objects.equals(widget.getId(), widgetId))
            .findFirst()
            .orElseThrow(() -> failure(PersonalDashboardCommandException.Code.NOT_FOUND, "Personal dashboard widget was not found."));
    }

    private PersonalDashboardWidget copyWidget(PersonalDashboardWidget source, Instant now) {
        PersonalDashboardWidget duplicate = new PersonalDashboardWidget();
        duplicate.setTitle(source.getTitle());
        duplicate.setDescription(source.getDescription());
        duplicate.setQueryDefinitionJson(source.getQueryDefinitionJson());
        duplicate.setQuerySchemaVersion(source.getQuerySchemaVersion());
        duplicate.setVisualizationType(source.getVisualizationType());
        duplicate.setVisualizationOptionsJson(source.getVisualizationOptionsJson());
        duplicate.setPosition(source.getPosition());
        duplicate.setWidth(source.getWidth());
        duplicate.setVisible(source.getVisible());
        duplicate.setCreatedAt(now);
        duplicate.setUpdatedAt(now);
        return duplicate;
    }

    private String copyTitle(String sourceTitle) {
        String suffix = " (copia)";
        String base = sourceTitle.length() + suffix.length() <= 120 ? sourceTitle : sourceTitle.substring(0, 120 - suffix.length()).trim();
        return title(base + suffix);
    }

    private PersonalDashboardDefaultView preferenceView(PersonalDashboardPreference preference, User owner) {
        if (preference == null) return new PersonalDashboardDefaultView(null, FINANCIAL_OVERVIEW_TEMPLATE_ID, null);
        PersonalDashboard selected = preference.getDefaultDashboard();
        Long dashboardId = selected != null && selected.getUser() != null && Objects.equals(selected.getUser().getId(), owner.getId())
            ? selected.getId()
            : null;
        if (dashboardId != null) return new PersonalDashboardDefaultView(dashboardId, null, preference.getVersion());
        String officialTemplateId = preference.getDefaultOfficialTemplateId();
        if (officialTemplateId == null || !OFFICIAL_TEMPLATE_IDS.contains(officialTemplateId)) {
            officialTemplateId = FINANCIAL_OVERVIEW_TEMPLATE_ID;
        }
        return new PersonalDashboardDefaultView(null, officialTemplateId, preference.getVersion());
    }

    private ValidatedWidget validateWidget(
        PersonalDashboard dashboard,
        String title,
        String description,
        SemanticQueryDefinition definition,
        PersonalDashboardVisualizationType visualizationType,
        JsonNode visualizationOptions,
        PersonalDashboardWidgetWidth width,
        Boolean visible
    ) {
        require(visualizationType != null, PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "A visualization type is required.");
        require(width != null, PersonalDashboardCommandException.Code.INVALID_LAYOUT, "A controlled widget width is required.");
        require(visible != null, PersonalDashboardCommandException.Code.INVALID_LAYOUT, "Widget visibility is required.");
        SemanticQueryDefinition normalizedDefinition;
        try {
            normalizedDefinition = semanticAnalyticsValidator.normalizeOwnedDefinitionForContextOrThrow(
                definition,
                readContext(dashboard.getDefaultContextJson())
            );
        } catch (SemanticAnalyticsValidationException exception) {
            throw failure(
                PersonalDashboardCommandException.Code.INVALID_QUERY,
                "The widget query is not a supported owned semantic query."
            );
        }
        validateVisualization(normalizedDefinition, visualizationType);
        validateWidgetWidth(visualizationType, width);
        JsonNode options = visualizationOptions == null ? JsonNodeFactory.instance.objectNode() : visualizationOptions;
        if (!options.isObject() || jsonDepth(options, 0) > MAX_JSON_DEPTH) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "Visualization options must be a bounded JSON object.");
        }
        return new ValidatedWidget(
            title(title),
            description(description),
            normalizedDefinition,
            visualizationType,
            write(options, MAX_VISUALIZATION_OPTIONS_BYTES, "Visualization options are too large."),
            width,
            visible
        );
    }

    private void validateVisualization(SemanticQueryDefinition definition, PersonalDashboardVisualizationType visualization) {
        if (!SemanticVisualizationCompatibility.isCompatible(definition, visualization)) {
            throw failure(
                PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
                "The visualization is incompatible with the semantic result shape."
            );
        }
    }

    private void validateWidgetWidth(PersonalDashboardVisualizationType visualizationType, PersonalDashboardWidgetWidth width) {
        boolean chart = Set.of(
            PersonalDashboardVisualizationType.LINE,
            PersonalDashboardVisualizationType.BAR,
            PersonalDashboardVisualizationType.GROUPED_BAR,
            PersonalDashboardVisualizationType.STACKED_BAR
        ).contains(visualizationType);
        if (chart && width == PersonalDashboardWidgetWidth.COMPACT) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_LAYOUT, "Chart widgets require a medium or full width.");
        }
    }

    private void apply(PersonalDashboardWidget widget, ValidatedWidget payload, Instant createdAt) {
        widget.setTitle(payload.title());
        widget.setDescription(payload.description());
        widget.setQueryDefinitionJson(write(payload.definition(), MAX_QUERY_JSON_BYTES, "Widget query is too large."));
        widget.setQuerySchemaVersion(SemanticAnalyticsSchema.VERSION);
        widget.setVisualizationType(payload.visualizationType());
        widget.setVisualizationOptionsJson(payload.visualizationOptionsJson());
        widget.setWidth(payload.width());
        widget.setVisible(payload.visible());
        widget.setCreatedAt(createdAt);
        widget.setUpdatedAt(Instant.now());
    }

    private Map<Long, PersonalDashboardLayoutCommand.WidgetLayoutItem> layoutItems(
        List<PersonalDashboardLayoutCommand.WidgetLayoutItem> items
    ) {
        Map<Long, PersonalDashboardLayoutCommand.WidgetLayoutItem> result = new LinkedHashMap<>();
        for (PersonalDashboardLayoutCommand.WidgetLayoutItem item : items) {
            if (item == null || item.widgetId() == null || item.widgetId() <= 0 || item.width() == null || item.visible() == null) {
                throw failure(
                    PersonalDashboardCommandException.Code.INVALID_LAYOUT,
                    "Every layout item needs a widget, width, and visibility."
                );
            }
            if (result.put(item.widgetId(), item) != null) {
                throw failure(PersonalDashboardCommandException.Code.INVALID_LAYOUT, "A widget may appear only once in a layout.");
            }
        }
        return result;
    }

    private Set<Long> widgetIds(PersonalDashboard dashboard) {
        return dashboard.getWidgets().stream().map(PersonalDashboardWidget::getId).collect(Collectors.toSet());
    }

    private int nextPosition(PersonalDashboard dashboard) {
        return dashboard.getWidgets().stream().mapToInt(PersonalDashboardWidget::getPosition).max().orElse(-1) + 1;
    }

    /** Keeps database order dense after deletion while avoiding the unique (dashboard_id, position) constraint mid-flush. */
    private void normalizeWidgetPositions(PersonalDashboard dashboard) {
        List<PersonalDashboardWidget> remaining = dashboard
            .getWidgets()
            .stream()
            .sorted(Comparator.comparing(PersonalDashboardWidget::getPosition).thenComparing(PersonalDashboardWidget::getId))
            .toList();
        int temporaryStart = remaining.stream().mapToInt(PersonalDashboardWidget::getPosition).max().orElse(-1) + remaining.size() + 1;
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPosition(temporaryStart + index);
        dashboardRepository.flush();
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPosition(index);
    }

    private void requireExpectedVersion(PersonalDashboard dashboard, Long expectedVersion) {
        if (expectedVersion == null || !Objects.equals(dashboard.getVersion(), expectedVersion)) {
            throw failure(
                PersonalDashboardCommandException.Code.VERSION_CONFLICT,
                "This dashboard was updated elsewhere. Reload it before saving."
            );
        }
    }

    private Optional<PersonalDashboardRequest> existingRequest(User owner, String scope, String requestId, String digest) {
        Optional<PersonalDashboardRequest> existing = requestRepository.findByOwnerScopeAndClientRequestId(owner.getId(), scope, requestId);
        if (existing.isPresent() && !Objects.equals(existing.orElseThrow().getRequestDigest(), digest)) {
            throw failure(
                PersonalDashboardCommandException.Code.IDEMPOTENCY_CONFLICT,
                "The client request ID was already used for different input."
            );
        }
        return existing;
    }

    private void saveRequest(User owner, String scope, String requestId, String digest, Long dashboardId, Long widgetId) {
        PersonalDashboardRequest receipt = new PersonalDashboardRequest();
        receipt.setUser(owner);
        receipt.setScope(scope);
        receipt.setClientRequestId(requestId);
        receipt.setRequestDigest(digest);
        receipt.setDashboardId(dashboardId);
        receipt.setWidgetId(widgetId);
        receipt.setCreatedAt(Instant.now());
        requestRepository.saveAndFlush(receipt);
    }

    private PersonalDashboardDetail replayDashboard(PersonalDashboardRequest request, User owner) {
        if (request.getDashboardId() == null) throw failure(
            PersonalDashboardCommandException.Code.IDEMPOTENCY_CONFLICT,
            "The prior request cannot be replayed."
        );
        return dashboardRepository
            .findOneWithWidgetsByIdAndUserLogin(request.getDashboardId(), owner.getLogin())
            .map(this::detail)
            .orElseThrow(() ->
                failure(PersonalDashboardCommandException.Code.IDEMPOTENCY_CONFLICT, "The prior request no longer has a dashboard.")
            );
    }

    private PersonalDashboardDetail replayWidgetDashboard(PersonalDashboardRequest request, User owner) {
        if (request.getDashboardId() == null || request.getWidgetId() == null) {
            throw failure(PersonalDashboardCommandException.Code.IDEMPOTENCY_CONFLICT, "The prior request cannot be replayed.");
        }
        PersonalDashboard dashboard = dashboardRepository
            .findOneWithWidgetsByIdAndUserLogin(request.getDashboardId(), owner.getLogin())
            .orElseThrow(() ->
                failure(PersonalDashboardCommandException.Code.IDEMPOTENCY_CONFLICT, "The prior request no longer has a dashboard.")
            );
        ownedWidget(dashboard, request.getWidgetId());
        return detail(dashboard);
    }

    private PersonalDashboardSummary summary(PersonalDashboard dashboard) {
        return new PersonalDashboardSummary(
            dashboard.getId(),
            dashboard.getName(),
            dashboard.getDescription(),
            dashboard.getVersion(),
            dashboard.getWidgets().size(),
            dashboard.getUpdatedAt()
        );
    }

    private PersonalDashboardDetail detail(PersonalDashboard dashboard) {
        return new PersonalDashboardDetail(
            dashboard.getId(),
            dashboard.getName(),
            dashboard.getDescription(),
            dashboard.getSchemaVersion(),
            dashboard.getVersion(),
            readContext(dashboard.getDefaultContextJson()),
            dashboard
                .getWidgets()
                .stream()
                .sorted(Comparator.comparing(PersonalDashboardWidget::getPosition))
                .map(this::widgetView)
                .toList(),
            dashboard.getCreatedAt(),
            dashboard.getUpdatedAt()
        );
    }

    private PersonalDashboardWidgetView widgetView(PersonalDashboardWidget widget) {
        try {
            return new PersonalDashboardWidgetView(
                widget.getId(),
                widget.getTitle(),
                widget.getDescription(),
                objectMapper.readValue(widget.getQueryDefinitionJson(), SemanticQueryDefinition.class),
                widget.getVisualizationType(),
                objectMapper.readTree(widget.getVisualizationOptionsJson()),
                widget.getPosition(),
                widget.getWidth(),
                Boolean.TRUE.equals(widget.getVisible()),
                widget.getCreatedAt(),
                widget.getUpdatedAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored personal dashboard widget is malformed.", exception);
        }
    }

    private VisualizationContext normalizeContext(VisualizationContext context) {
        VisualizationContext candidate = context == null
            ? new VisualizationContext(
                SemanticAnalyticsSchema.VERSION,
                new VisualizationTime("RELATIVE", "THIS_MONTH", null, null),
                List.of(),
                List.of()
            )
            : context;
        try {
            return semanticAnalyticsValidator.normalizeContextOrThrow(candidate);
        } catch (SemanticAnalyticsValidationException exception) {
            throw failure(
                PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
                "The dashboard context is not valid for the current user."
            );
        }
    }

    private VisualizationContext readContext(String json) {
        try {
            return objectMapper.readValue(json, VisualizationContext.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored personal dashboard context is malformed.", exception);
        }
    }

    private String write(Object value, int maximumBytes, String oversizedMessage) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
                throw failure(PersonalDashboardCommandException.Code.INVALID_PAYLOAD, oversizedMessage);
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "Dashboard data cannot be serialized.");
        }
    }

    private String digest(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create a request digest.", exception);
        }
    }

    private int jsonDepth(JsonNode node, int depth) {
        if (depth > MAX_JSON_DEPTH) return depth;
        int maximum = depth;
        for (JsonNode child : node) maximum = Math.max(maximum, jsonDepth(child, depth + 1));
        return maximum;
    }

    private String name(String candidate) {
        String value = candidate == null ? "" : candidate.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 100) {
            throw failure(
                PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
                "Dashboard and widget titles must be between 1 and 100 characters."
            );
        }
        return value;
    }

    private String title(String candidate) {
        String value = candidate == null ? "" : candidate.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 120) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "Widget titles must be between 1 and 120 characters.");
        }
        return value;
    }

    private String description(String candidate) {
        if (candidate == null || candidate.trim().isEmpty()) return null;
        String value = candidate.trim();
        if (value.length() > 500) throw failure(
            PersonalDashboardCommandException.Code.INVALID_PAYLOAD,
            "Descriptions may contain at most 500 characters."
        );
        return value;
    }

    private String normalizedName(String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private String clientRequestId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 100 || !result.matches("[A-Za-z0-9._:-]+")) {
            throw failure(PersonalDashboardCommandException.Code.INVALID_PAYLOAD, "clientRequestId must be a bounded opaque identifier.");
        }
        return result;
    }

    private void touch(PersonalDashboard dashboard) {
        dashboard.setUpdatedAt(Instant.now());
    }

    private static void require(boolean condition, PersonalDashboardCommandException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }

    private static PersonalDashboardCommandException failure(PersonalDashboardCommandException.Code code, String message) {
        return new PersonalDashboardCommandException(code, message);
    }

    private record ValidatedWidget(
        String title,
        String description,
        SemanticQueryDefinition definition,
        PersonalDashboardVisualizationType visualizationType,
        String visualizationOptionsJson,
        PersonalDashboardWidgetWidth width,
        boolean visible
    ) {}

    private record DuplicateRequestFingerprint(Long sourceDashboardId, PersonalDashboardDuplicateCommand command) {}

    private record DuplicateWidgetRequestFingerprint(Long dashboardId, Long widgetId, PersonalDashboardWidgetDuplicateCommand command) {}
}
