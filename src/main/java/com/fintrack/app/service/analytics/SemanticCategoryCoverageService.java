package com.fintrack.app.service.analytics;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only presentation adapter for category scopes. It deliberately delegates hierarchy traversal to the same
 * owner-scoped reference catalog used by {@link SemanticTransactionLedgerExecutor} for DESCENDANT_OF predicates.
 * It does not decide which categories a Budget may select or calculate any financial amount.
 */
@Service
@Transactional(readOnly = true)
public class SemanticCategoryCoverageService {

    private final SemanticAnalyticsReferenceResolver referenceResolver;

    SemanticCategoryCoverageService(SemanticAnalyticsReferenceResolver referenceResolver) {
        this.referenceResolver = referenceResolver;
    }

    public CategoryCoverage resolve(String ownerLogin, Collection<Long> requestedCategoryIds) {
        SemanticAnalyticsReferenceCatalog references = referenceResolver.resolve(ownerLogin);
        List<Long> requested = requestedCategoryIds == null
            ? List.of()
            : requestedCategoryIds.stream().filter(Objects::nonNull).distinct().toList();

        List<Long> missing = requested.stream().filter(id -> !references.hasCategory(id)).toList();
        List<Category> selected = requested.stream().filter(references::hasCategory).map(id -> category(references, id)).toList();

        Set<Long> effectiveIds = new LinkedHashSet<>();
        requested.stream().filter(references::hasCategory).forEach(id -> effectiveIds.addAll(references.descendantsInclusive(id)));
        List<Category> effective = effectiveIds
            .stream()
            .map(id -> category(references, id))
            .sorted(Comparator.comparing(value -> String.join("\u0000", value.path()), String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new CategoryCoverage(List.copyOf(selected), List.copyOf(effective), List.copyOf(missing));
    }

    private Category category(SemanticAnalyticsReferenceCatalog references, Long id) {
        SemanticAnalyticsReferenceCatalog.CategoryReference reference = references.category(id);
        return new Category(
            reference.id(),
            reference.name(),
            reference.color(),
            reference.active(),
            references.categoryPath(reference.id()),
            reference.categoryType()
        );
    }

    public record Category(
        Long id,
        String name,
        String color,
        Boolean active,
        List<String> path,
        com.fintrack.app.domain.enumeration.CategoryType categoryType
    ) {
        public Category {
            path = List.copyOf(path);
        }
    }

    public record CategoryCoverage(List<Category> selectedCategories, List<Category> effectiveCategories, List<Long> missingCategoryIds) {
        public CategoryCoverage {
            selectedCategories = List.copyOf(selectedCategories);
            effectiveCategories = List.copyOf(effectiveCategories);
            missingCategoryIds = List.copyOf(missingCategoryIds);
        }
    }
}
