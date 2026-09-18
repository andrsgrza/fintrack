package com.fintrack.app.service;

import com.fintrack.app.domain.Tag;

/**
 * Guards new Tag assignments. Inactive tags are historical references only: existing associations are preserved but
 * new manual or automatic associations are not allowed.
 */
public final class TagReferenceValidator {

    private TagReferenceValidator() {}

    public static void validateActiveForNewReference(Tag tag) {
        if (tag != null && !Boolean.TRUE.equals(tag.getActive())) {
            throw new IllegalArgumentException("Inactive tag cannot be selected for a new reference");
        }
    }
}
