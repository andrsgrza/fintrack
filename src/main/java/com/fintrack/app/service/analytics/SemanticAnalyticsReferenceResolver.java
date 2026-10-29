package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves only the authenticated owner's readable analytical references. */
@Service
@Transactional(readOnly = true)
class SemanticAnalyticsReferenceResolver {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;

    SemanticAnalyticsReferenceResolver(CategoryRepository categoryRepository, TagRepository tagRepository) {
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    SemanticAnalyticsReferenceCatalog resolve(String ownerLogin) {
        List<SemanticAnalyticsReferenceCatalog.CategoryReference> categories = categoryRepository
            .findAllWithToOneRelationshipsByUserLogin(ownerLogin)
            .stream()
            .map(this::category)
            .toList();
        List<SemanticAnalyticsReferenceCatalog.TagReference> tags = tagRepository
            .findAllWithToOneRelationshipsByUserLogin(ownerLogin)
            .stream()
            .map(this::tag)
            .toList();
        return new SemanticAnalyticsReferenceCatalog(categories, tags);
    }

    private SemanticAnalyticsReferenceCatalog.CategoryReference category(Category category) {
        return new SemanticAnalyticsReferenceCatalog.CategoryReference(
            category.getId(),
            category.getName(),
            category.getColor(),
            category.getActive(),
            category.getParentCategory() == null ? null : category.getParentCategory().getId()
        );
    }

    private SemanticAnalyticsReferenceCatalog.TagReference tag(Tag tag) {
        return new SemanticAnalyticsReferenceCatalog.TagReference(tag.getId(), tag.getName(), tag.getColor(), tag.getActive());
    }
}
