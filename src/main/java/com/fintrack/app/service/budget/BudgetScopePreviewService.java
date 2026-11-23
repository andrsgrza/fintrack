package com.fintrack.app.service.budget;

import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.analytics.SemanticCategoryCoverageService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes a read-only Budget presentation projection while preserving the semantic analytics hierarchy resolver as
 * the sole authority for descendant matching.
 */
@Service
@Transactional(readOnly = true)
public class BudgetScopePreviewService {

    private final CurrentUserService currentUserService;
    private final SemanticCategoryCoverageService categoryCoverageService;

    public BudgetScopePreviewService(CurrentUserService currentUserService, SemanticCategoryCoverageService categoryCoverageService) {
        this.currentUserService = currentUserService;
        this.categoryCoverageService = categoryCoverageService;
    }

    public BudgetScopePreview preview(BudgetScopePreviewCommand command) {
        List<Long> requested = command == null || command.categoryIds() == null ? List.of() : command.categoryIds();
        if (requested.isEmpty()) return new BudgetScopePreview(true, List.of(), List.of(), List.of(), List.of());

        SemanticCategoryCoverageService.CategoryCoverage coverage = categoryCoverageService.resolve(
            currentUserService.getCurrentUserLogin(),
            requested
        );
        List<Long> incompatible = coverage
            .selectedCategories()
            .stream()
            .filter(category -> !BudgetCategoryCompatibility.supports(category.categoryType(), command.type()))
            .map(SemanticCategoryCoverageService.Category::id)
            .toList();
        return new BudgetScopePreview(
            false,
            coverage.selectedCategories().stream().map(this::category).toList(),
            coverage.effectiveCategories().stream().map(this::category).toList(),
            coverage.missingCategoryIds(),
            incompatible
        );
    }

    private BudgetScopePreview.Category category(SemanticCategoryCoverageService.Category source) {
        return new BudgetScopePreview.Category(
            source.id(),
            source.name(),
            source.color(),
            source.active(),
            source.path(),
            source.categoryType() == null ? null : source.categoryType().name()
        );
    }
}
