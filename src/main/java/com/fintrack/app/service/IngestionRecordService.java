package com.fintrack.app.service;

import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.repository.IngestionRecordRepository;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.mapper.IngestionRecordMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.IngestionRecord}.
 */
@Service
@Transactional
public class IngestionRecordService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionRecordService.class);

    private final IngestionRecordRepository ingestionRecordRepository;

    private final IngestionRecordMapper ingestionRecordMapper;

    public IngestionRecordService(IngestionRecordRepository ingestionRecordRepository, IngestionRecordMapper ingestionRecordMapper) {
        this.ingestionRecordRepository = ingestionRecordRepository;
        this.ingestionRecordMapper = ingestionRecordMapper;
    }

    /**
     * Save a ingestionRecord.
     *
     * @param ingestionRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public IngestionRecordDTO save(IngestionRecordDTO ingestionRecordDTO) {
        LOG.debug("Request to save IngestionRecord : {}", ingestionRecordDTO);
        IngestionRecord ingestionRecord = ingestionRecordMapper.toEntity(ingestionRecordDTO);
        ingestionRecord = ingestionRecordRepository.save(ingestionRecord);
        return ingestionRecordMapper.toDto(ingestionRecord);
    }

    /**
     * Update a ingestionRecord.
     *
     * @param ingestionRecordDTO the entity to save.
     * @return the persisted entity.
     */
    public IngestionRecordDTO update(IngestionRecordDTO ingestionRecordDTO) {
        LOG.debug("Request to update IngestionRecord : {}", ingestionRecordDTO);
        IngestionRecord ingestionRecord = ingestionRecordMapper.toEntity(ingestionRecordDTO);
        ingestionRecord = ingestionRecordRepository.save(ingestionRecord);
        return ingestionRecordMapper.toDto(ingestionRecord);
    }

    /**
     * Partially update a ingestionRecord.
     *
     * @param ingestionRecordDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<IngestionRecordDTO> partialUpdate(IngestionRecordDTO ingestionRecordDTO) {
        LOG.debug("Request to partially update IngestionRecord : {}", ingestionRecordDTO);

        return ingestionRecordRepository
            .findById(ingestionRecordDTO.getId())
            .map(existingIngestionRecord -> {
                ingestionRecordMapper.partialUpdate(existingIngestionRecord, ingestionRecordDTO);

                return existingIngestionRecord;
            })
            .map(ingestionRecordRepository::save)
            .map(ingestionRecordMapper::toDto);
    }

    /**
     * Get one ingestionRecord by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<IngestionRecordDTO> findOne(Long id) {
        LOG.debug("Request to get IngestionRecord : {}", id);
        return ingestionRecordRepository.findById(id).map(ingestionRecordMapper::toDto);
    }

    /**
     * Delete the ingestionRecord by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete IngestionRecord : {}", id);
        ingestionRecordRepository.deleteById(id);
    }
}
