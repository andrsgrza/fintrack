package com.fintrack.app.service;

import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.repository.InternalTransferRepository;
import com.fintrack.app.service.dto.InternalTransferDTO;
import com.fintrack.app.service.mapper.InternalTransferMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.InternalTransfer}.
 */
@Service
@Transactional
public class InternalTransferService {

    private static final Logger LOG = LoggerFactory.getLogger(InternalTransferService.class);

    private final InternalTransferRepository internalTransferRepository;

    private final InternalTransferMapper internalTransferMapper;

    public InternalTransferService(InternalTransferRepository internalTransferRepository, InternalTransferMapper internalTransferMapper) {
        this.internalTransferRepository = internalTransferRepository;
        this.internalTransferMapper = internalTransferMapper;
    }

    /**
     * Save a internalTransfer.
     *
     * @param internalTransferDTO the entity to save.
     * @return the persisted entity.
     */
    public InternalTransferDTO save(InternalTransferDTO internalTransferDTO) {
        LOG.debug("Request to save InternalTransfer : {}", internalTransferDTO);
        InternalTransfer internalTransfer = internalTransferMapper.toEntity(internalTransferDTO);
        internalTransfer = internalTransferRepository.save(internalTransfer);
        return internalTransferMapper.toDto(internalTransfer);
    }

    /**
     * Update a internalTransfer.
     *
     * @param internalTransferDTO the entity to save.
     * @return the persisted entity.
     */
    public InternalTransferDTO update(InternalTransferDTO internalTransferDTO) {
        LOG.debug("Request to update InternalTransfer : {}", internalTransferDTO);
        InternalTransfer internalTransfer = internalTransferMapper.toEntity(internalTransferDTO);
        internalTransfer = internalTransferRepository.save(internalTransfer);
        return internalTransferMapper.toDto(internalTransfer);
    }

    /**
     * Partially update a internalTransfer.
     *
     * @param internalTransferDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<InternalTransferDTO> partialUpdate(InternalTransferDTO internalTransferDTO) {
        LOG.debug("Request to partially update InternalTransfer : {}", internalTransferDTO);

        return internalTransferRepository
            .findById(internalTransferDTO.getId())
            .map(existingInternalTransfer -> {
                internalTransferMapper.partialUpdate(existingInternalTransfer, internalTransferDTO);

                return existingInternalTransfer;
            })
            .map(internalTransferRepository::save)
            .map(internalTransferMapper::toDto);
    }

    /**
     * Get all the internalTransfers.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<InternalTransferDTO> findAll() {
        LOG.debug("Request to get all InternalTransfers");
        return internalTransferRepository
            .findAll()
            .stream()
            .map(internalTransferMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one internalTransfer by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<InternalTransferDTO> findOne(Long id) {
        LOG.debug("Request to get InternalTransfer : {}", id);
        return internalTransferRepository.findById(id).map(internalTransferMapper::toDto);
    }

    /**
     * Delete the internalTransfer by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete InternalTransfer : {}", id);
        internalTransferRepository.deleteById(id);
    }
}
