package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.TagMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.Tag}.
 */
@Service
@Transactional
public class TagService {

    private static final Logger LOG = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;

    private final TagMapper tagMapper;

    private final CurrentUserService currentUserService;

    public TagService(TagRepository tagRepository, TagMapper tagMapper, CurrentUserService currentUserService) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
        this.currentUserService = currentUserService;
    }

    /**
     * Save a tag.
     *
     * @param tagDTO the entity to save.
     * @return the persisted entity.
     */
    public TagDTO save(TagDTO tagDTO) {
        LOG.debug("Request to save Tag : {}", tagDTO);
        Tag tag = tagMapper.toEntity(tagDTO);
        tag.setUser(currentUserService.getCurrentUser());
        Instant now = Instant.now();
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        tag.setName(normalizeName(tag.getName()));
        validateUniqueNameForOwner(tag.getUser().getId(), tag.getName(), null);
        tag = tagRepository.save(tag);
        return tagMapper.toDto(tag);
    }

    /**
     * Update a tag.
     *
     * @param tagDTO the entity to save.
     * @return the persisted entity.
     */
    public TagDTO update(TagDTO tagDTO) {
        LOG.debug("Request to update Tag : {}", tagDTO);
        Tag existingTag = findAccessibleEntity(tagDTO.getId()).orElseThrow();
        rejectTimestampChange(existingTag.getCreatedAt(), tagDTO.getCreatedAt(), "Created at cannot be changed");
        rejectTimestampChange(existingTag.getUpdatedAt(), tagDTO.getUpdatedAt(), "Updated at cannot be changed");
        Tag tag = tagMapper.toEntity(tagDTO);
        tag.setName(normalizeName(tag.getName()));
        validateUniqueNameForOwner(existingTag.getUser().getId(), tag.getName(), existingTag.getId());
        tag.setUser(existingTag.getUser());
        tag.setCreatedAt(existingTag.getCreatedAt());
        tag.setUpdatedAt(Instant.now());
        tag = tagRepository.save(tag);
        return tagMapper.toDto(tag);
    }

    /**
     * Partially update a tag.
     *
     * @param tagDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TagDTO> partialUpdate(TagDTO tagDTO) {
        return partialUpdate(tagDTO, null);
    }

    /**
     * Partially update a tag, applying immutable timestamp checks only when present in the patch body.
     *
     * @param tagDTO the entity to update partially.
     * @param patchNode the raw patch payload.
     * @return the persisted entity.
     */
    public Optional<TagDTO> partialUpdate(TagDTO tagDTO, JsonNode patchNode) {
        LOG.debug("Request to partially update Tag : {}", tagDTO);

        return findAccessibleEntity(tagDTO.getId())
            .map(existingTag -> {
                Instant existingCreatedAt = existingTag.getCreatedAt();
                Instant existingUpdatedAt = existingTag.getUpdatedAt();

                rejectTimestampChanges(existingTag, tagDTO, patchNode);
                tagMapper.partialUpdate(existingTag, tagDTO);
                existingTag.setCreatedAt(existingCreatedAt);
                existingTag.setUpdatedAt(Instant.now());
                if (tagDTO.getName() != null) {
                    existingTag.setName(normalizeName(tagDTO.getName()));
                    validateUniqueNameForOwner(existingTag.getUser().getId(), existingTag.getName(), existingTag.getId());
                }
                return existingTag;
            })
            .map(tagRepository::save)
            .map(tagMapper::toDto);
    }

    /**
     * Get all the tags with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<TagDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return tagRepository.findAllWithEagerRelationships(pageable).map(tagMapper::toDto);
        }
        return tagRepository
            .findAllWithToOneRelationshipsByUserLogin(currentUserService.getCurrentUserLogin(), pageable)
            .map(tagMapper::toDto);
    }

    /**
     * Get one tag by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TagDTO> findOne(Long id) {
        LOG.debug("Request to get Tag : {}", id);
        return findAccessibleEntity(id).map(tagMapper::toDto);
    }

    /**
     * Returns whether the current user can access the tag.
     *
     * @param id the id of the entity.
     * @return true when the tag exists and is visible to the current user.
     */
    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    /**
     * Delete the tag by id.
     *
     * @param id the id of the entity.
     * @return true when the tag was deleted.
     */
    public boolean delete(Long id) {
        LOG.debug("Request to delete Tag : {}", id);
        Optional<Tag> tag = findAccessibleEntity(id);
        if (tag.isEmpty()) {
            return false;
        }
        Long tagId = tag.orElseThrow().getId();
        unlinkTagFromAllRelationships(tagId);
        tagRepository.deleteById(tagId);
        return true;
    }

    private void unlinkTagFromAllRelationships(Long tagId) {
        tagRepository.deleteFinancialTransactionTagLinksByTagId(tagId);
        tagRepository.deleteTransactionRuleResultingTagLinksByTagId(tagId);
        tagRepository.deleteFinancialSubscriptionTagLinksByTagId(tagId);
        tagRepository.deleteBudgetTagLinksByTagId(tagId);
    }

    private Optional<Tag> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return tagRepository.findOneWithEagerRelationships(id);
        }
        return tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void rejectTimestampChanges(Tag existingTag, TagDTO tagDTO, JsonNode patchNode) {
        if (patchNode == null) {
            if (tagDTO.getCreatedAt() != null) {
                rejectTimestampChange(existingTag.getCreatedAt(), tagDTO.getCreatedAt(), "Created at cannot be changed");
            }
            if (tagDTO.getUpdatedAt() != null) {
                rejectTimestampChange(existingTag.getUpdatedAt(), tagDTO.getUpdatedAt(), "Updated at cannot be changed");
            }
            return;
        }
        if (patchNode.has("createdAt")) {
            rejectTimestampChange(existingTag.getCreatedAt(), tagDTO.getCreatedAt(), "Created at cannot be changed");
        }
        if (patchNode.has("updatedAt")) {
            rejectTimestampChange(existingTag.getUpdatedAt(), tagDTO.getUpdatedAt(), "Updated at cannot be changed");
        }
    }

    private void rejectTimestampChange(Instant existingTimestamp, Instant requestedTimestamp, String message) {
        if (requestedTimestamp == null || !Objects.equals(existingTimestamp, requestedTimestamp)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    private void validateUniqueNameForOwner(Long userId, String name, Long excludeTagId) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (tagRepository.existsByUserIdAndNormalizedName(userId, name, excludeTagId)) {
            throw new IllegalArgumentException("Tag name already exists");
        }
    }
}
