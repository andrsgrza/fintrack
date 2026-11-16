package com.fintrack.app.web.rest;

import com.fintrack.app.service.budget.BudgetCreateCommand;
import com.fintrack.app.service.budget.BudgetCreated;
import com.fintrack.app.service.budget.BudgetCreationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Product command boundary: Budget-1 intentionally exposes creation only, never generic entity CRUD. */
@RestController
@RequestMapping("/api/budgets")
@PreAuthorize("isAuthenticated()")
public class BudgetResource {

    private final BudgetCreationService creationService;

    public BudgetResource(BudgetCreationService creationService) {
        this.creationService = creationService;
    }

    @PostMapping
    public ResponseEntity<BudgetCreated> create(@Valid @RequestBody BudgetCreateCommand command) {
        BudgetCreated created = creationService.create(command);
        return ResponseEntity.created(URI.create("/api/budgets/" + created.id())).body(created);
    }
}
