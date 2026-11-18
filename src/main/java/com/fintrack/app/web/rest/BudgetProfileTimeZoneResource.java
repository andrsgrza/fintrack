package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetProfileTimeZoneCommand;
import com.fintrack.app.service.budget.BudgetProfileTimeZoneService;
import com.fintrack.app.service.budget.BudgetProfileTimeZoneView;
import com.fintrack.app.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Profile-zone reads and writes are separate from generic account edits so they cannot bypass Budget history. */
@RestController
@RequestMapping("/api/account/budget-time-zone")
@PreAuthorize("isAuthenticated()")
public class BudgetProfileTimeZoneResource {

    private static final String ENTITY_NAME = "budgetTimeZone";

    private final BudgetProfileTimeZoneService service;

    public BudgetProfileTimeZoneResource(BudgetProfileTimeZoneService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<BudgetProfileTimeZoneView> current() {
        return ResponseEntity.ok(service.current());
    }

    @PutMapping
    public ResponseEntity<BudgetProfileTimeZoneView> update(@Valid @RequestBody BudgetProfileTimeZoneCommand command) {
        try {
            return ResponseEntity.ok(service.update(command));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestAlertException(exception.getMessage(), ENTITY_NAME, "invalid");
        }
    }
}
