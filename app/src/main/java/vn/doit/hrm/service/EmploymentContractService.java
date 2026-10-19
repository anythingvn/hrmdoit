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
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.mapper.EmploymentContractMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.EmploymentContract}.
 */
@Service
@Transactional
public class EmploymentContractService {

    private static final Logger LOG = LoggerFactory.getLogger(EmploymentContractService.class);

    private final EmploymentContractRepository employmentContractRepository;

    private final EmploymentContractMapper employmentContractMapper;

    public EmploymentContractService(
        EmploymentContractRepository employmentContractRepository,
        EmploymentContractMapper employmentContractMapper
    ) {
        this.employmentContractRepository = employmentContractRepository;
        this.employmentContractMapper = employmentContractMapper;
    }

    /**
     * Save a employmentContract.
     *
     * @param employmentContractDTO the entity to save.
     * @return the persisted entity.
     */
    public EmploymentContractDTO save(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to save EmploymentContract : {}", employmentContractDTO);
        EmploymentContract employmentContract = employmentContractMapper.toEntity(employmentContractDTO);
        employmentContract = employmentContractRepository.save(employmentContract);
        return employmentContractMapper.toDto(employmentContract);
    }

    /**
     * Update a employmentContract.
     *
     * @param employmentContractDTO the entity to save.
     * @return the persisted entity.
     */
    public EmploymentContractDTO update(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to update EmploymentContract : {}", employmentContractDTO);
        EmploymentContract employmentContract = employmentContractMapper.toEntity(employmentContractDTO);
        employmentContract = employmentContractRepository.save(employmentContract);
        return employmentContractMapper.toDto(employmentContract);
    }

    /**
     * Partially update a employmentContract.
     *
     * @param employmentContractDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<EmploymentContractDTO> partialUpdate(EmploymentContractDTO employmentContractDTO) {
        LOG.debug("Request to partially update EmploymentContract : {}", employmentContractDTO);

        return employmentContractRepository
            .findById(employmentContractDTO.getId())
            .map(existingEmploymentContract -> {
                employmentContractMapper.partialUpdate(existingEmploymentContract, employmentContractDTO);

                return existingEmploymentContract;
            })
            .map(employmentContractRepository::save)
            .map(employmentContractMapper::toDto);
    }

    /**
     * Get all the employmentContracts.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<EmploymentContractDTO> findAll() {
        LOG.debug("Request to get all EmploymentContracts");
        return employmentContractRepository
            .findAll()
            .stream()
            .map(employmentContractMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the employmentContracts with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<EmploymentContractDTO> findAllWithEagerRelationships(Pageable pageable) {
        return employmentContractRepository.findAllWithEagerRelationships(pageable).map(employmentContractMapper::toDto);
    }

    /**
     * Get one employmentContract by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<EmploymentContractDTO> findOne(Long id) {
        LOG.debug("Request to get EmploymentContract : {}", id);
        return employmentContractRepository.findOneWithEagerRelationships(id).map(employmentContractMapper::toDto);
    }

    /**
     * Delete the employmentContract by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete EmploymentContract : {}", id);
        employmentContractRepository.deleteById(id);
    }
}
