package com.fintrack.app.web.rest;

import com.fintrack.app.service.dashboard.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Owner-scoped commands for the future personal-dashboard gallery and viewer. These routes deliberately accept
 * command DTOs rather than exposing generated CRUD for JPA entities.
 */
@RestController
@RequestMapping("/api/personal-dashboards")
@PreAuthorize("isAuthenticated()")
public class PersonalDashboardResource {

    private final PersonalDashboardCommandService commandService;

    public PersonalDashboardResource(PersonalDashboardCommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping
    public ResponseEntity<List<PersonalDashboardSummary>> list() {
        return ResponseEntity.ok(commandService.listOwn());
    }

    @PostMapping
    public ResponseEntity<PersonalDashboardDetail> create(@RequestBody PersonalDashboardCreateCommand command) {
        PersonalDashboardDetail detail = commandService.create(command);
        return ResponseEntity.created(URI.create("/api/personal-dashboards/" + detail.id())).body(detail);
    }

    @PostMapping("/from-template")
    public ResponseEntity<PersonalDashboardDetail> copyOfficialTemplate(@RequestBody PersonalDashboardTemplateCopyCommand command) {
        PersonalDashboardDetail detail = commandService.copyOfficialTemplate(command);
        return ResponseEntity.created(URI.create("/api/personal-dashboards/" + detail.id())).body(detail);
    }

    @GetMapping("/{dashboardId}")
    public ResponseEntity<PersonalDashboardDetail> get(@PathVariable Long dashboardId) {
        return commandService.findOwn(dashboardId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A missing preference intentionally resolves to the official Financial Overview. */
    @GetMapping("/default")
    public ResponseEntity<PersonalDashboardDefaultView> getDefault() {
        return ResponseEntity.ok(commandService.defaultOwn());
    }

    @PutMapping("/default")
    public ResponseEntity<PersonalDashboardDefaultView> setDefault(@RequestBody PersonalDashboardDefaultCommand command) {
        return ResponseEntity.ok(commandService.setDefault(command));
    }

    @PutMapping("/{dashboardId}")
    public ResponseEntity<PersonalDashboardDetail> update(
        @PathVariable Long dashboardId,
        @RequestBody PersonalDashboardUpdateCommand command
    ) {
        return ResponseEntity.ok(commandService.update(dashboardId, command));
    }

    @PatchMapping("/{dashboardId}/metadata")
    public ResponseEntity<PersonalDashboardDetail> updateMetadata(
        @PathVariable Long dashboardId,
        @RequestBody PersonalDashboardMetadataUpdateCommand command
    ) {
        return ResponseEntity.ok(commandService.updateMetadata(dashboardId, command));
    }

    @PostMapping("/{dashboardId}/duplicate")
    public ResponseEntity<PersonalDashboardDetail> duplicate(
        @PathVariable Long dashboardId,
        @RequestBody PersonalDashboardDuplicateCommand command
    ) {
        PersonalDashboardDetail duplicate = commandService.duplicate(dashboardId, command);
        return ResponseEntity.created(URI.create("/api/personal-dashboards/" + duplicate.id())).body(duplicate);
    }

    @DeleteMapping("/{dashboardId}")
    public ResponseEntity<Void> delete(@PathVariable Long dashboardId, @RequestParam Long expectedVersion) {
        commandService.delete(dashboardId, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{dashboardId}/widgets")
    public ResponseEntity<PersonalDashboardDetail> addWidget(
        @PathVariable Long dashboardId,
        @RequestBody PersonalDashboardWidgetAddCommand command
    ) {
        return ResponseEntity.ok(commandService.addWidget(dashboardId, command));
    }

    @PutMapping("/{dashboardId}/widgets/{widgetId}")
    public ResponseEntity<PersonalDashboardDetail> updateWidget(
        @PathVariable Long dashboardId,
        @PathVariable Long widgetId,
        @RequestBody PersonalDashboardWidgetUpdateCommand command
    ) {
        return ResponseEntity.ok(commandService.updateWidget(dashboardId, widgetId, command));
    }

    @DeleteMapping("/{dashboardId}/widgets/{widgetId}")
    public ResponseEntity<PersonalDashboardDetail> removeWidget(
        @PathVariable Long dashboardId,
        @PathVariable Long widgetId,
        @RequestParam Long expectedVersion
    ) {
        return ResponseEntity.ok(commandService.removeWidget(dashboardId, widgetId, expectedVersion));
    }

    @PutMapping("/{dashboardId}/layout")
    public ResponseEntity<PersonalDashboardDetail> saveLayout(
        @PathVariable Long dashboardId,
        @RequestBody PersonalDashboardLayoutCommand command
    ) {
        return ResponseEntity.ok(commandService.saveLayout(dashboardId, command));
    }
}
