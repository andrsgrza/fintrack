package com.fintrack.app.service;

import com.fintrack.app.domain.Tag;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.mapper.TagMapper;
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
        Tag tag = tagMapper.toEntity(tagDTO);
        tag.setUser(existingTag.getUser());
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
        LOG.debug("Request to partially update Tag : {}", tagDTO);

        return findAccessibleEntity(tagDTO.getId())
            .map(existingTag -> {
                tagMapper.partialUpdate(existingTag, tagDTO);
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
        tagRepository.deleteById(id);
        return true;
    }

    private Optional<Tag> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return tagRepository.findOneWithEagerRelationships(id);
        }
        return tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }
}
