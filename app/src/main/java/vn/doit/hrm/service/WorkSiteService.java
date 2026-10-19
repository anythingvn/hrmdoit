package vn.doit.hrm.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.dto.WorkSiteDTO;
import vn.doit.hrm.service.mapper.WorkSiteMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.WorkSite}.
 */
@Service
@Transactional
public class WorkSiteService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkSiteService.class);

    private final WorkSiteRepository workSiteRepository;

    private final WorkSiteMapper workSiteMapper;

    public WorkSiteService(WorkSiteRepository workSiteRepository, WorkSiteMapper workSiteMapper) {
        this.workSiteRepository = workSiteRepository;
        this.workSiteMapper = workSiteMapper;
    }

    /**
     * Save a workSite.
     *
     * @param workSiteDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkSiteDTO save(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to save WorkSite : {}", workSiteDTO);
        WorkSite workSite = workSiteMapper.toEntity(workSiteDTO);
        workSite = workSiteRepository.save(workSite);
        return workSiteMapper.toDto(workSite);
    }

    /**
     * Update a workSite.
     *
     * @param workSiteDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkSiteDTO update(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to update WorkSite : {}", workSiteDTO);
        WorkSite workSite = workSiteMapper.toEntity(workSiteDTO);
        workSite = workSiteRepository.save(workSite);
        return workSiteMapper.toDto(workSite);
    }

    /**
     * Partially update a workSite.
     *
     * @param workSiteDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<WorkSiteDTO> partialUpdate(WorkSiteDTO workSiteDTO) {
        LOG.debug("Request to partially update WorkSite : {}", workSiteDTO);

        return workSiteRepository
            .findById(workSiteDTO.getId())
            .map(existingWorkSite -> {
                workSiteMapper.partialUpdate(existingWorkSite, workSiteDTO);

                return existingWorkSite;
            })
            .map(workSiteRepository::save)
            .map(workSiteMapper::toDto);
    }

    /**
     * Get all the workSites.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<WorkSiteDTO> findAll() {
        LOG.debug("Request to get all WorkSites");
        return workSiteRepository.findAll().stream().map(workSiteMapper::toDto).collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one workSite by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<WorkSiteDTO> findOne(Long id) {
        LOG.debug("Request to get WorkSite : {}", id);
        return workSiteRepository.findById(id).map(workSiteMapper::toDto);
    }

    /**
     * Delete the workSite by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete WorkSite : {}", id);
        workSiteRepository.deleteById(id);
    }
}
