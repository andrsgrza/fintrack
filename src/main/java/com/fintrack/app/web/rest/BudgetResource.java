package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetCommandService;
import com.fintrack.app.service.budget.BudgetCreateCommand;
import com.fintrack.app.service.budget.BudgetCreated;
import com.fintrack.app.service.budget.BudgetCreationService;
import com.fintrack.app.service.budget.BudgetHistoryView;
import com.fintrack.app.service.budget.BudgetLifecycleCommand;
import com.fintrack.app.service.budget.BudgetMetadataUpdateCommand;
import com.fintrack.app.service.budget.BudgetMutationResult;
import com.fintrack.app.service.budget.BudgetOperationalPeriodView;
import com.fintrack.app.service.budget.BudgetRevisionScheduleCommand;
import com.fintrack.app.service.budget.BudgetVersionedCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Product command boundary: Budget-1 intentionally exposes creation only, never generic entity CRUD. */
@RestController
@RequestMapping("/api/budgets")
@PreAuthorize("isAuthenticated()")
public class BudgetResource {

    private final BudgetCreationService creationService;
    private final BudgetCommandService commandService;

    public BudgetResource(BudgetCreationService creationService, BudgetCommandService commandService) {
        this.creationService = creationService;
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<BudgetCreated> create(@Valid @RequestBody BudgetCreateCommand command) {
        BudgetCreated created = creationService.create(command);
        return ResponseEntity.created(URI.create("/api/budgets/" + created.id())).body(created);
    }

    @PutMapping("/{budgetId}/metadata")
    public ResponseEntity<BudgetMutationResult> updateMetadata(
        @PathVariable Long budgetId,
        @Valid @RequestBody BudgetMetadataUpdateCommand command
    ) {
        return ResponseEntity.ok(commandService.updateMetadata(budgetId, command));
    }

    @PostMapping("/{budgetId}/revisions")
    public ResponseEntity<BudgetMutationResult> scheduleRevision(
        @PathVariable Long budgetId,
        @Valid @RequestBody BudgetRevisionScheduleCommand command
    ) {
        return ResponseEntity.ok(commandService.scheduleRevision(budgetId, command));
    }

    @PostMapping("/{budgetId}/revisions/{scheduleId}/replace")
    public ResponseEntity<BudgetMutationResult> replaceRevision(
        @PathVariable Long budgetId,
        @PathVariable Long scheduleId,
        @Valid @RequestBody BudgetRevisionScheduleCommand command
    ) {
        return ResponseEntity.ok(commandService.replaceFutureRevision(budgetId, scheduleId, command));
    }

    @PostMapping("/{budgetId}/revisions/{scheduleId}/cancel")
    public ResponseEntity<BudgetMutationResult> cancelRevision(
        @PathVariable Long budgetId,
        @PathVariable Long scheduleId,
        @Valid @RequestBody BudgetVersionedCommand command
    ) {
        return ResponseEntity.ok(commandService.cancelFutureRevision(budgetId, scheduleId, command));
    }

    @PostMapping("/{budgetId}/pause")
    public ResponseEntity<BudgetMutationResult> pause(@PathVariable Long budgetId, @Valid @RequestBody BudgetLifecycleCommand command) {
        return ResponseEntity.ok(commandService.requestPause(budgetId, command));
    }

    @PostMapping("/{budgetId}/pause/{eventId}/cancel")
    public ResponseEntity<BudgetMutationResult> cancelPause(
        @PathVariable Long budgetId,
        @PathVariable Long eventId,
        @Valid @RequestBody BudgetLifecycleCommand command
    ) {
        return ResponseEntity.ok(commandService.cancelPendingPause(budgetId, eventId, command));
    }

    @PostMapping("/{budgetId}/resume")
    public ResponseEntity<BudgetMutationResult> resume(@PathVariable Long budgetId, @Valid @RequestBody BudgetLifecycleCommand command) {
        return ResponseEntity.ok(commandService.requestResume(budgetId, command));
    }

    @PostMapping("/{budgetId}/finish")
    public ResponseEntity<BudgetMutationResult> finish(@PathVariable Long budgetId, @Valid @RequestBody BudgetLifecycleCommand command) {
        return ResponseEntity.ok(commandService.requestFinish(budgetId, command));
    }

    @GetMapping("/{budgetId}/effective-revision")
    public ResponseEntity<BudgetHistoryView.Revision> effectiveRevision(@PathVariable Long budgetId, @RequestParam LocalDate periodStart) {
        return ResponseEntity.ok(commandService.effectiveRevision(budgetId, periodStart));
    }

    @GetMapping("/{budgetId}/history")
    public ResponseEntity<BudgetHistoryView> history(@PathVariable Long budgetId) {
        return ResponseEntity.ok(commandService.history(budgetId));
    }

    @GetMapping("/{budgetId}/periods")
    public ResponseEntity<List<BudgetOperationalPeriodView>> periods(
        @PathVariable Long budgetId,
        @RequestParam long firstIndex,
        @RequestParam long lastIndex,
        @RequestParam String timeZone
    ) {
        return ResponseEntity.ok(commandService.periods(budgetId, firstIndex, lastIndex, timeZone));
    }
}
