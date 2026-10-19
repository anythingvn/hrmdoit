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
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.service.mapper.EmployeeDocumentMapper;

/**
 * Service Implementation for managing {@link vn.doit.hrm.domain.EmployeeDocument}.
 */
@Service
@Transactional
public class EmployeeDocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocumentService.class);

    private final EmployeeDocumentRepository employeeDocumentRepository;

    private final EmployeeDocumentMapper employeeDocumentMapper;

    public EmployeeDocumentService(EmployeeDocumentRepository employeeDocumentRepository, EmployeeDocumentMapper employeeDocumentMapper) {
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.employeeDocumentMapper = employeeDocumentMapper;
    }

    /**
     * Save a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDocumentDTO save(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to save EmployeeDocument : {}", employeeDocumentDTO);
        EmployeeDocument employeeDocument = employeeDocumentMapper.toEntity(employeeDocumentDTO);
        employeeDocument = employeeDocumentRepository.save(employeeDocument);
        return employeeDocumentMapper.toDto(employeeDocument);
    }

    /**
     * Update a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to save.
     * @return the persisted entity.
     */
    public EmployeeDocumentDTO update(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to update EmployeeDocument : {}", employeeDocumentDTO);
        EmployeeDocument employeeDocument = employeeDocumentMapper.toEntity(employeeDocumentDTO);
        employeeDocument = employeeDocumentRepository.save(employeeDocument);
        return employeeDocumentMapper.toDto(employeeDocument);
    }

    /**
     * Partially update a employeeDocument.
     *
     * @param employeeDocumentDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<EmployeeDocumentDTO> partialUpdate(EmployeeDocumentDTO employeeDocumentDTO) {
        LOG.debug("Request to partially update EmployeeDocument : {}", employeeDocumentDTO);

        return employeeDocumentRepository
            .findById(employeeDocumentDTO.getId())
            .map(existingEmployeeDocument -> {
                employeeDocumentMapper.partialUpdate(existingEmployeeDocument, employeeDocumentDTO);

                return existingEmployeeDocument;
            })
            .map(employeeDocumentRepository::save)
            .map(employeeDocumentMapper::toDto);
    }

    /**
     * Get all the employeeDocuments.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<EmployeeDocumentDTO> findAll() {
        LOG.debug("Request to get all EmployeeDocuments");
        return employeeDocumentRepository
            .findAll()
            .stream()
            .map(employeeDocumentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the employeeDocuments with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<EmployeeDocumentDTO> findAllWithEagerRelationships(Pageable pageable) {
        return employeeDocumentRepository.findAllWithEagerRelationships(pageable).map(employeeDocumentMapper::toDto);
    }

    /**
     * Get one employeeDocument by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeDocumentDTO> findOne(Long id) {
        LOG.debug("Request to get EmployeeDocument : {}", id);
        return employeeDocumentRepository.findOneWithEagerRelationships(id).map(employeeDocumentMapper::toDto);
    }

    /**
     * Delete the employeeDocument by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete EmployeeDocument : {}", id);
        employeeDocumentRepository.deleteById(id);
    }
}
