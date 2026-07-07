package com.fintrack.app.service;

import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.repository.FileIngestionRepository;
import com.fintrack.app.service.dto.FileIngestionDTO;
import com.fintrack.app.service.mapper.FileIngestionMapper;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.FileIngestion}.
 */
@Service
@Transactional
public class FileIngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(FileIngestionService.class);

    private final FileIngestionRepository fileIngestionRepository;

    private final FileIngestionMapper fileIngestionMapper;

    public FileIngestionService(FileIngestionRepository fileIngestionRepository, FileIngestionMapper fileIngestionMapper) {
        this.fileIngestionRepository = fileIngestionRepository;
        this.fileIngestionMapper = fileIngestionMapper;
    }

    /**
     * Save a fileIngestion.
     *
     * @param fileIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public FileIngestionDTO save(FileIngestionDTO fileIngestionDTO) {
        LOG.debug("Request to save FileIngestion : {}", fileIngestionDTO);
        FileIngestion fileIngestion = fileIngestionMapper.toEntity(fileIngestionDTO);
        fileIngestion = fileIngestionRepository.save(fileIngestion);
        return fileIngestionMapper.toDto(fileIngestion);
    }

    /**
     * Update a fileIngestion.
     *
     * @param fileIngestionDTO the entity to save.
     * @return the persisted entity.
     */
    public FileIngestionDTO update(FileIngestionDTO fileIngestionDTO) {
        LOG.debug("Request to update FileIngestion : {}", fileIngestionDTO);
        FileIngestion fileIngestion = fileIngestionMapper.toEntity(fileIngestionDTO);
        fileIngestion = fileIngestionRepository.save(fileIngestion);
        return fileIngestionMapper.toDto(fileIngestion);
    }

    /**
     * Partially update a fileIngestion.
     *
     * @param fileIngestionDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<FileIngestionDTO> partialUpdate(FileIngestionDTO fileIngestionDTO) {
        LOG.debug("Request to partially update FileIngestion : {}", fileIngestionDTO);

        return fileIngestionRepository
            .findById(fileIngestionDTO.getId())
            .map(existingFileIngestion -> {
                fileIngestionMapper.partialUpdate(existingFileIngestion, fileIngestionDTO);

                return existingFileIngestion;
            })
            .map(fileIngestionRepository::save)
            .map(fileIngestionMapper::toDto);
    }

    /**
     * Get all the fileIngestions.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<FileIngestionDTO> findAll() {
        LOG.debug("Request to get all FileIngestions");
        return fileIngestionRepository.findAll().stream().map(fileIngestionMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one fileIngestion by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<FileIngestionDTO> findOne(Long id) {
        LOG.debug("Request to get FileIngestion : {}", id);
        return fileIngestionRepository.findById(id).map(fileIngestionMapper::toDto);
    }

    /**
     * Delete the fileIngestion by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete FileIngestion : {}", id);
        fileIngestionRepository.deleteById(id);
    }
}
