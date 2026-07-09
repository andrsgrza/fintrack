package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.repository.TransactionIngestionRepository;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.mapper.TransactionIngestionMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionIngestion}.
 */
@Service
@Transactional
public class TransactionIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionIngestionService.class);

    private final TransactionIngestionRepository transactionIngestionRepository;

    private final TransactionIngestionMapper transactionIngestionMapper;

    public TransactionIngestionService(
        TransactionIngestionRepository transactionIngestionRepository,
        TransactionIngestionMapper transactionIngestionMapper
    ) {
        this.transactionIngestionRepository = transactionIngestionRepository;
        this.transactionIngestionMapper = transactionIngestionMapper;
    }

    /**
     * Save a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionIngestionDTO save(TransactionIngestionDTO transactionIngestionDTO) {
        LOG.debug("Request to save TransactionIngestion : {}", transactionIngestionDTO);
        TransactionIngestion transactionIngestion = transactionIngestionMapper.toEntity(transactionIngestionDTO);
        transactionIngestion = transactionIngestionRepository.save(transactionIngestion);
        return transactionIngestionMapper.toDto(transactionIngestion);
    }

    /**
     * Update a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public TransactionIngestionDTO update(TransactionIngestionDTO transactionIngestionDTO) {
        LOG.debug("Request to update TransactionIngestion : {}", transactionIngestionDTO);
        TransactionIngestion transactionIngestion = transactionIngestionMapper.toEntity(transactionIngestionDTO);
        transactionIngestion = transactionIngestionRepository.save(transactionIngestion);
        return transactionIngestionMapper.toDto(transactionIngestion);
    }

    /**
     * Partially update a transactionIngestion.
     *
     * @param transactionIngestionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<TransactionIngestionDTO> partialUpdate(TransactionIngestionDTO transactionIngestionDTO) {
        LOG.debug("Request to partially update TransactionIngestion : {}", transactionIngestionDTO);

        return transactionIngestionRepository
            .findById(transactionIngestionDTO.getId())
            .map(existingTransactionIngestion -> {
                transactionIngestionMapper.partialUpdate(existingTransactionIngestion, transactionIngestionDTO);

                return existingTransactionIngestion;
            })
            .map(transactionIngestionRepository::save)
            .map(transactionIngestionMapper::toDto);
    }

    /**
     * Get all the transactionIngestions with eager load of relationships.
     *
     * @return the list of entities.
     */
    public Page<TransactionIngestionDTO> findAllWithEagerRelationships(Pageable pageable) {
        return transactionIngestionRepository.findAllWithEagerRelationships(pageable).map(transactionIngestionMapper::toDto);
    }

    /**
     *  Get all the transactionIngestions where FileIngestion is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<TransactionIngestionDTO> findAllWhereFileIngestionIsNull() {
        LOG.debug("Request to get all transactionIngestions where FileIngestion is null");
        return StreamSupport.stream(transactionIngestionRepository.findAll().spliterator(), false)
            .filter(transactionIngestion -> transactionIngestion.getFileIngestion() == null)
            .map(transactionIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the transactionIngestions where ApiIngestion is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<TransactionIngestionDTO> findAllWhereApiIngestionIsNull() {
        LOG.debug("Request to get all transactionIngestions where ApiIngestion is null");
        return StreamSupport.stream(transactionIngestionRepository.findAll().spliterator(), false)
            .filter(transactionIngestion -> transactionIngestion.getApiIngestion() == null)
            .map(transactionIngestionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one transactionIngestion by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<TransactionIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionIngestion : {}", id);
        return transactionIngestionRepository.findOneWithEagerRelationships(id).map(transactionIngestionMapper::toDto);
    }

    /**
     * Delete the transactionIngestion by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete TransactionIngestion : {}", id);
        transactionIngestionRepository.deleteById(id);
    }
}
