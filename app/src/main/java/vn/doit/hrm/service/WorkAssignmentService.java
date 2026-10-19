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
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.service.mapper.WorkAssignmentMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.WorkAssignment}.
 */
@Service
@Transactional
public class WorkAssignmentService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkAssignmentService.class);

    private final WorkAssignmentRepository workAssignmentRepository;

    private final WorkAssignmentMapper workAssignmentMapper;

    public WorkAssignmentService(WorkAssignmentRepository workAssignmentRepository, WorkAssignmentMapper workAssignmentMapper) {
        this.workAssignmentRepository = workAssignmentRepository;
        this.workAssignmentMapper = workAssignmentMapper;
    }

    /**
     * Save a workAssignment.
     *
     * @param workAssignmentDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkAssignmentDTO save(WorkAssignmentDTO workAssignmentDTO) {
        LOG.debug("Request to save WorkAssignment : {}", workAssignmentDTO);
        WorkAssignment workAssignment = workAssignmentMapper.toEntity(workAssignmentDTO);
        workAssignment = workAssignmentRepository.save(workAssignment);
        return workAssignmentMapper.toDto(workAssignment);
    }

    /**
     * Update a workAssignment.
     *
     * @param workAssignmentDTO the entity to save.
     * @return the persisted entity.
     */
    public WorkAssignmentDTO update(WorkAssignmentDTO workAssignmentDTO) {
        LOG.debug("Request to update WorkAssignment : {}", workAssignmentDTO);
        WorkAssignment workAssignment = workAssignmentMapper.toEntity(workAssignmentDTO);
        workAssignment = workAssignmentRepository.save(workAssignment);
        return workAssignmentMapper.toDto(workAssignment);
    }

    /**
     * Partially update a workAssignment.
     *
     * @param workAssignmentDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<WorkAssignmentDTO> partialUpdate(WorkAssignmentDTO workAssignmentDTO) {
        LOG.debug("Request to partially update WorkAssignment : {}", workAssignmentDTO);

        return workAssignmentRepository
            .findById(workAssignmentDTO.getId())
            .map(existingWorkAssignment -> {
                workAssignmentMapper.partialUpdate(existingWorkAssignment, workAssignmentDTO);

                return existingWorkAssignment;
            })
            .map(workAssignmentRepository::save)
            .map(workAssignmentMapper::toDto);
    }

    /**
     * Get all the workAssignments.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<WorkAssignmentDTO> findAll() {
        LOG.debug("Request to get all WorkAssignments");
        return workAssignmentRepository
            .findAll()
            .stream()
            .map(workAssignmentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the workAssignments with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<WorkAssignmentDTO> findAllWithEagerRelationships(Pageable pageable) {
        return workAssignmentRepository.findAllWithEagerRelationships(pageable).map(workAssignmentMapper::toDto);
    }

    /**
     * Get one workAssignment by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<WorkAssignmentDTO> findOne(Long id) {
        LOG.debug("Request to get WorkAssignment : {}", id);
        return workAssignmentRepository.findOneWithEagerRelationships(id).map(workAssignmentMapper::toDto);
    }

    /**
     * Delete the workAssignment by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete WorkAssignment : {}", id);
        workAssignmentRepository.deleteById(id);
    }
}
