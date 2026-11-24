package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.enumeration.CategoryType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-resolved, owner-scoped category and tag references. This is deliberately not client supplied and keeps
 * hierarchy traversal out of persistence paths exposed by the query language.
 */
final class SemanticAnalyticsReferenceCatalog {

    static final String UNCATEGORIZED_ID = "UNCATEGORIZED";
    static final String DIRECT_ASSIGNMENT_ID = "DIRECT_ASSIGNMENT";

    record CategoryReference(Long id, String name, String color, Boolean active, Long parentId, CategoryType categoryType) {}

    record TagReference(Long id, String name, String color, Boolean active) {}

    private final Map<Long, CategoryReference> categories;
    private final Map<Long, TagReference> tags;

    SemanticAnalyticsReferenceCatalog(List<CategoryReference> categoryReferences, List<TagReference> tagReferences) {
        Map<Long, CategoryReference> categoryMap = new LinkedHashMap<>();
        for (CategoryReference category : categoryReferences) {
            categoryMap.put(category.id(), category);
        }
        categories = Map.copyOf(categoryMap);
        Map<Long, TagReference> tagMap = new LinkedHashMap<>();
        for (TagReference tag : tagReferences) {
            tagMap.put(tag.id(), tag);
        }
        tags = Map.copyOf(tagMap);
        validateHierarchy();
    }

    boolean hasCategory(Long id) {
        return categories.containsKey(id);
    }

    boolean hasTag(Long id) {
        return tags.containsKey(id);
    }

    Set<Long> tagIds() {
        return tags.keySet();
    }

    Set<Long> categoryIds() {
        return categories.keySet();
    }

    CategoryReference category(Long id) {
        return categories.get(id);
    }

    TagReference tag(Long id) {
        return tags.get(id);
    }

    Set<Long> descendantsInclusive(Long categoryId) {
        if (!hasCategory(categoryId)) return Set.of();
        Set<Long> descendants = new HashSet<>();
        for (Long candidate : categories.keySet()) {
            if (isDescendantOrSelf(candidate, categoryId)) descendants.add(candidate);
        }
        return Set.copyOf(descendants);
    }

    boolean isDescendantOrSelf(Long categoryId, Long ancestorId) {
        Long current = categoryId;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            if (current.equals(ancestorId)) return true;
            CategoryReference category = categories.get(current);
            current = category == null ? null : category.parentId();
        }
        return false;
    }

    Long rootId(Long categoryId) {
        CategoryReference category = category(categoryId);
        if (category == null) return null;
        Long current = category.id();
        Set<Long> visited = new HashSet<>();
        while (visited.add(current)) {
            CategoryReference currentCategory = category(current);
            if (currentCategory == null || currentCategory.parentId() == null) return current;
            current = currentCategory.parentId();
        }
        return null;
    }

    /** Returns the first descendant below the root, or the root itself for directly assigned transactions. */
    Long firstSubcategoryId(Long categoryId) {
        Long root = rootId(categoryId);
        if (root == null || root.equals(categoryId)) return root;
        Long current = categoryId;
        Long previous = current;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            CategoryReference category = category(current);
            if (category == null || category.parentId() == null) return previous;
            previous = current;
            current = category.parentId();
            if (root.equals(current)) return previous;
        }
        return null;
    }

    /** Returns the immediate child below a selected parent, or null for a direct assignment to that parent. */
    Long immediateChildBelow(Long selectedParentId, Long categoryId) {
        if (selectedParentId == null || categoryId == null || selectedParentId.equals(categoryId)) return null;
        Long current = categoryId;
        Long previous = current;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            CategoryReference category = category(current);
            if (category == null) return null;
            if (selectedParentId.equals(category.parentId())) return current;
            previous = current;
            current = category.parentId();
        }
        return null;
    }

    SemanticDimensionMetadata categoryMetadata(Long categoryId) {
        CategoryReference category = category(categoryId);
        if (category == null) return null;
        return new SemanticDimensionMetadata(
            "CATEGORY:" + category.id(),
            category.name(),
            category.color(),
            category.active(),
            false,
            categoryPath(category.id())
        );
    }

    SemanticDimensionMetadata tagMetadata(Long tagId) {
        TagReference tag = tag(tagId);
        if (tag == null) return null;
        return new SemanticDimensionMetadata("TAG:" + tag.id(), tag.name(), tag.color(), tag.active(), false, List.of(tag.name()));
    }

    SemanticDimensionMetadata uncategorizedMetadata() {
        return new SemanticDimensionMetadata(UNCATEGORIZED_ID, "Uncategorized", null, null, true, List.of("Uncategorized"));
    }

    SemanticDimensionMetadata directAssignmentMetadata(Long parentId) {
        CategoryReference parent = category(parentId);
        String name = parent == null ? "Direct assignment" : "Directly in " + parent.name();
        return new SemanticDimensionMetadata(
            DIRECT_ASSIGNMENT_ID + ":" + parentId,
            name,
            parent == null ? null : parent.color(),
            parent == null ? null : parent.active(),
            true,
            parent == null ? List.of(name) : categoryPath(parentId)
        );
    }

    List<String> categoryPath(Long categoryId) {
        List<String> reversed = new ArrayList<>();
        Long current = categoryId;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            CategoryReference category = category(current);
            if (category == null) break;
            reversed.add(category.name());
            current = category.parentId();
        }
        java.util.Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private void validateHierarchy() {
        for (CategoryReference category : categories.values()) {
            if (category.parentId() != null && !categories.containsKey(category.parentId())) {
                throw new IllegalArgumentException("Category parent is outside the owner-scoped category hierarchy");
            }
            if (rootId(category.id()) == null) {
                throw new IllegalArgumentException("Category hierarchy contains a cycle");
            }
        }
    }
}
