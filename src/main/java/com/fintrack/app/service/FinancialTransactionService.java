package com.fintrack.app.service;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.mapper.FinancialTransactionMapper;
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
 * Service Implementation for managing {@link com.fintrack.app.domain.FinancialTransaction}.
 */
@Service
@Transactional
public class FinancialTransactionService {

    private static final Logger LOG = LoggerFactory.getLogger(FinancialTransactionService.class);

    private final FinancialTransactionRepository financialTransactionRepository;

    private final FinancialTransactionMapper financialTransactionMapper;

    public FinancialTransactionService(
        FinancialTransactionRepository financialTransactionRepository,
        FinancialTransactionMapper financialTransactionMapper
    ) {
        this.financialTransactionRepository = financialTransactionRepository;
        this.financialTransactionMapper = financialTransactionMapper;
    }

    /**
     * Save a financialTransaction.
     *
     * @param financialTransactionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialTransactionDTO save(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to save FinancialTransaction : {}", financialTransactionDTO);
        FinancialTransaction financialTransaction = financialTransactionMapper.toEntity(financialTransactionDTO);
        financialTransaction = financialTransactionRepository.save(financialTransaction);
        return financialTransactionMapper.toDto(financialTransaction);
    }

    /**
     * Update a financialTransaction.
     *
     * @param financialTransactionDTO the entity to save.
     * @return the persisted entity.
     */
    public FinancialTransactionDTO update(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to update FinancialTransaction : {}", financialTransactionDTO);
        FinancialTransaction financialTransaction = financialTransactionMapper.toEntity(financialTransactionDTO);
        financialTransaction = financialTransactionRepository.save(financialTransaction);
        return financialTransactionMapper.toDto(financialTransaction);
    }

    /**
     * Partially update a financialTransaction.
     *
     * @param financialTransactionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FinancialTransactionDTO> partialUpdate(FinancialTransactionDTO financialTransactionDTO) {
        LOG.debug("Request to partially update FinancialTransaction : {}", financialTransactionDTO);

        return financialTransactionRepository
            .findById(financialTransactionDTO.getId())
            .map(existingFinancialTransaction -> {
                financialTransactionMapper.partialUpdate(existingFinancialTransaction, financialTransactionDTO);

                return existingFinancialTransaction;
            })
            .map(financialTransactionRepository::save)
            .map(financialTransactionMapper::toDto);
    }

    /**
     * Get all the financialTransactions with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<FinancialTransactionDTO> findAllWithEagerRelationships(Pageable pageable) {
        return financialTransactionRepository.findAllWithEagerRelationships(pageable).map(financialTransactionMapper::toDto);
    }

    /**
     *  Get all the financialTransactions where OutgoingInternalTransfer is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereOutgoingInternalTransferIsNull() {
        LOG.debug("Request to get all financialTransactions where OutgoingInternalTransfer is null");
        return StreamSupport.stream(financialTransactionRepository.findAll().spliterator(), false)
            .filter(financialTransaction -> financialTransaction.getOutgoingInternalTransfer() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the financialTransactions where IncomingInternalTransfer is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereIncomingInternalTransferIsNull() {
        LOG.debug("Request to get all financialTransactions where IncomingInternalTransfer is null");
        return StreamSupport.stream(financialTransactionRepository.findAll().spliterator(), false)
            .filter(financialTransaction -> financialTransaction.getIncomingInternalTransfer() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     *  Get all the financialTransactions where IngestionRecord is {@code null}.
     *  @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FinancialTransactionDTO> findAllWhereIngestionRecordIsNull() {
        LOG.debug("Request to get all financialTransactions where IngestionRecord is null");
        return StreamSupport.stream(financialTransactionRepository.findAll().spliterator(), false)
            .filter(financialTransaction -> financialTransaction.getIngestionRecord() == null)
            .map(financialTransactionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one financialTransaction by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FinancialTransactionDTO> findOne(Long id) {
        LOG.debug("Request to get FinancialTransaction : {}", id);
        return financialTransactionRepository.findOneWithEagerRelationships(id).map(financialTransactionMapper::toDto);
    }

    /**
     * Delete the financialTransaction by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete FinancialTransaction : {}", id);
        financialTransactionRepository.deleteById(id);
    }
}
