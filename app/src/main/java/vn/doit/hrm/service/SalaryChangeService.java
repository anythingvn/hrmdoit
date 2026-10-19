package vn.doit.hrm.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.dto.SalaryChangeDTO;
import vn.doit.hrm.service.mapper.SalaryChangeMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.SalaryChange}.
 */
@Service
@Transactional
public class SalaryChangeService {

    private static final Logger LOG = LoggerFactory.getLogger(SalaryChangeService.class);

    private final SalaryChangeRepository salaryChangeRepository;

    private final SalaryChangeMapper salaryChangeMapper;

    public SalaryChangeService(SalaryChangeRepository salaryChangeRepository, SalaryChangeMapper salaryChangeMapper) {
        this.salaryChangeRepository = salaryChangeRepository;
        this.salaryChangeMapper = salaryChangeMapper;
    }

    /**
     * Save a salaryChange.
     *
     * @param salaryChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public SalaryChangeDTO save(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to save SalaryChange : {}", salaryChangeDTO);
        SalaryChange salaryChange = salaryChangeMapper.toEntity(salaryChangeDTO);
        salaryChange = salaryChangeRepository.save(salaryChange);
        return salaryChangeMapper.toDto(salaryChange);
    }

    /**
     * Update a salaryChange.
     *
     * @param salaryChangeDTO the entity to save.
     * @return the persisted entity.
     */
    public SalaryChangeDTO update(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to update SalaryChange : {}", salaryChangeDTO);
        SalaryChange salaryChange = salaryChangeMapper.toEntity(salaryChangeDTO);
        salaryChange = salaryChangeRepository.save(salaryChange);
        return salaryChangeMapper.toDto(salaryChange);
    }

    /**
     * Partially update a salaryChange.
     *
     * @param salaryChangeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<SalaryChangeDTO> partialUpdate(SalaryChangeDTO salaryChangeDTO) {
        LOG.debug("Request to partially update SalaryChange : {}", salaryChangeDTO);

        return salaryChangeRepository
            .findById(salaryChangeDTO.getId())
            .map(existingSalaryChange -> {
                salaryChangeMapper.partialUpdate(existingSalaryChange, salaryChangeDTO);

                return existingSalaryChange;
            })
            .map(salaryChangeRepository::save)
            .map(salaryChangeMapper::toDto);
    }

    /**
     * Get all the salaryChanges.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<SalaryChangeDTO> findAll() {
        LOG.debug("Request to get all SalaryChanges");
        return salaryChangeRepository.findAll().stream().map(salaryChangeMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the salaryChanges with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<SalaryChangeDTO> findAllWithEagerRelationships(Pageable pageable) {
        return salaryChangeRepository.findAllWithEagerRelationships(pageable).map(salaryChangeMapper::toDto);
    }

    /**
     * Get one salaryChange by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<SalaryChangeDTO> findOne(Long id) {
        LOG.debug("Request to get SalaryChange : {}", id);
        return salaryChangeRepository.findOneWithEagerRelationships(id).map(salaryChangeMapper::toDto);
    }

    /**
     * Delete the salaryChange by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete SalaryChange : {}", id);
        salaryChangeRepository.deleteById(id);
    }
}
