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
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.service.dto.AllowanceDTO;
import vn.doit.hrm.service.mapper.AllowanceMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.Allowance}.
 */
@Service
@Transactional
public class AllowanceService {

    private static final Logger LOG = LoggerFactory.getLogger(AllowanceService.class);

    private final AllowanceRepository allowanceRepository;

    private final AllowanceMapper allowanceMapper;

    public AllowanceService(AllowanceRepository allowanceRepository, AllowanceMapper allowanceMapper) {
        this.allowanceRepository = allowanceRepository;
        this.allowanceMapper = allowanceMapper;
    }

    /**
     * Save a allowance.
     *
     * @param allowanceDTO the entity to save.
     * @return the persisted entity.
     */
    public AllowanceDTO save(AllowanceDTO allowanceDTO) {
        LOG.debug("Request to save Allowance : {}", allowanceDTO);
        Allowance allowance = allowanceMapper.toEntity(allowanceDTO);
        allowance = allowanceRepository.save(allowance);
        return allowanceMapper.toDto(allowance);
    }

    /**
     * Update a allowance.
     *
     * @param allowanceDTO the entity to save.
     * @return the persisted entity.
     */
    public AllowanceDTO update(AllowanceDTO allowanceDTO) {
        LOG.debug("Request to update Allowance : {}", allowanceDTO);
        Allowance allowance = allowanceMapper.toEntity(allowanceDTO);
        allowance = allowanceRepository.save(allowance);
        return allowanceMapper.toDto(allowance);
    }

    /**
     * Partially update a allowance.
     *
     * @param allowanceDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<AllowanceDTO> partialUpdate(AllowanceDTO allowanceDTO) {
        LOG.debug("Request to partially update Allowance : {}", allowanceDTO);

        return allowanceRepository
            .findById(allowanceDTO.getId())
            .map(existingAllowance -> {
                allowanceMapper.partialUpdate(existingAllowance, allowanceDTO);

                return existingAllowance;
            })
            .map(allowanceRepository::save)
            .map(allowanceMapper::toDto);
    }

    /**
     * Get all the allowances.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<AllowanceDTO> findAll() {
        LOG.debug("Request to get all Allowances");
        return allowanceRepository.findAll().stream().map(allowanceMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the allowances with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<AllowanceDTO> findAllWithEagerRelationships(Pageable pageable) {
        return allowanceRepository.findAllWithEagerRelationships(pageable).map(allowanceMapper::toDto);
    }

    /**
     * Get one allowance by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<AllowanceDTO> findOne(Long id) {
        LOG.debug("Request to get Allowance : {}", id);
        return allowanceRepository.findOneWithEagerRelationships(id).map(allowanceMapper::toDto);
    }

    /**
     * Delete the allowance by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Allowance : {}", id);
        allowanceRepository.deleteById(id);
    }
}
