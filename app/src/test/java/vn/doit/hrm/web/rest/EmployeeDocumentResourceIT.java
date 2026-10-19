package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.EmployeeDocumentAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.repository.EmployeeDocumentRepository;
import vn.doit.hrm.service.EmployeeDocumentService;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;
import vn.doit.hrm.service.mapper.EmployeeDocumentMapper;

/**
 * Integration tests for the {@link EmployeeDocumentResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class EmployeeDocumentResourceIT {

    private static final String DEFAULT_FILE_NAME = "AAAAAAAAAA";
    private static final String UPDATED_FILE_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CONTENT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_CONTENT_TYPE = "BBBBBBBBBB";

    private static final Long DEFAULT_SIZE_BYTES = 1L;
    private static final Long UPDATED_SIZE_BYTES = 2L;

    private static final String DEFAULT_STORAGE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_STORAGE_PATH = "BBBBBBBBBB";

    private static final String DEFAULT_UPLOADED_BY = "AAAAAAAAAA";
    private static final String UPDATED_UPLOADED_BY = "BBBBBBBBBB";

    private static final Instant DEFAULT_UPLOADED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_UPLOADED_AT = Instant.ofEpochMilli(1792404748666L);

    private static final String ENTITY_API_URL = "/api/employee-documents";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeDocumentRepository employeeDocumentRepository;

    @Mock
    private EmployeeDocumentRepository employeeDocumentRepositoryMock;

    @Autowired
    private EmployeeDocumentMapper employeeDocumentMapper;

    @Mock
    private EmployeeDocumentService employeeDocumentServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restEmployeeDocumentMockMvc;

    private EmployeeDocument employeeDocument;

    private EmployeeDocument insertedEmployeeDocument;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static EmployeeDocument createEntity(EntityManager em) {
        EmployeeDocument employeeDocument = new EmployeeDocument()
            .fileName(DEFAULT_FILE_NAME)
            .contentType(DEFAULT_CONTENT_TYPE)
            .sizeBytes(DEFAULT_SIZE_BYTES)
            .storagePath(DEFAULT_STORAGE_PATH)
            .uploadedBy(DEFAULT_UPLOADED_BY)
            .uploadedAt(DEFAULT_UPLOADED_AT);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        employeeDocument.setEmployee(employee);
        return employeeDocument;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static EmployeeDocument createUpdatedEntity(EntityManager em) {
        EmployeeDocument updatedEmployeeDocument = new EmployeeDocument()
            .fileName(UPDATED_FILE_NAME)
            .contentType(UPDATED_CONTENT_TYPE)
            .sizeBytes(UPDATED_SIZE_BYTES)
            .storagePath(UPDATED_STORAGE_PATH)
            .uploadedBy(UPDATED_UPLOADED_BY)
            .uploadedAt(UPDATED_UPLOADED_AT);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createUpdatedEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        updatedEmployeeDocument.setEmployee(employee);
        return updatedEmployeeDocument;
    }

    @BeforeEach
    void initTest() {
        employeeDocument = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedEmployeeDocument != null) {
            employeeDocumentRepository.delete(insertedEmployeeDocument);
            insertedEmployeeDocument = null;
        }
    }

    @Test
    @Transactional
    void getAllEmployeeDocuments() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        // Get all the employeeDocumentList
        restEmployeeDocumentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(employeeDocument.getId().intValue())))
            .andExpect(jsonPath("$.[*].fileName").value(hasItem(DEFAULT_FILE_NAME)))
            .andExpect(jsonPath("$.[*].contentType").value(hasItem(DEFAULT_CONTENT_TYPE)))
            .andExpect(jsonPath("$.[*].sizeBytes").value(hasItem(DEFAULT_SIZE_BYTES.intValue())))
            .andExpect(jsonPath("$.[*].storagePath").doesNotExist())
            .andExpect(jsonPath("$.[*].uploadedBy").value(hasItem(DEFAULT_UPLOADED_BY)))
            .andExpect(jsonPath("$.[*].uploadedAt").value(hasItem(DEFAULT_UPLOADED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeeDocumentsWithEagerRelationshipsIsEnabled() throws Exception {
        when(employeeDocumentServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restEmployeeDocumentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(employeeDocumentServiceMock, times(1)).findAllWithEagerRelationships();
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeeDocumentsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(employeeDocumentServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restEmployeeDocumentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(employeeDocumentRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getEmployeeDocument() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        // Get the employeeDocument
        restEmployeeDocumentMockMvc
            .perform(get(ENTITY_API_URL_ID, employeeDocument.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(employeeDocument.getId().intValue()))
            .andExpect(jsonPath("$.fileName").value(DEFAULT_FILE_NAME))
            .andExpect(jsonPath("$.contentType").value(DEFAULT_CONTENT_TYPE))
            .andExpect(jsonPath("$.sizeBytes").value(DEFAULT_SIZE_BYTES.intValue()))
            .andExpect(jsonPath("$.storagePath").doesNotExist())
            .andExpect(jsonPath("$.uploadedBy").value(DEFAULT_UPLOADED_BY))
            .andExpect(jsonPath("$.uploadedAt").value(DEFAULT_UPLOADED_AT.toString()));
    }

    @Test
    @Transactional
    void getNonExistingEmployeeDocument() throws Exception {
        // Get the employeeDocument
        restEmployeeDocumentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void deleteEmployeeDocument() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the employeeDocument
        restEmployeeDocumentMockMvc
            .perform(delete(ENTITY_API_URL_ID, employeeDocument.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return employeeDocumentRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected EmployeeDocument getPersistedEmployeeDocument(EmployeeDocument employeeDocument) {
        return employeeDocumentRepository.findById(employeeDocument.getId()).orElseThrow();
    }

    protected void assertPersistedEmployeeDocumentToMatchAllProperties(EmployeeDocument expectedEmployeeDocument) {
        assertEmployeeDocumentAllPropertiesEquals(expectedEmployeeDocument, getPersistedEmployeeDocument(expectedEmployeeDocument));
    }

    protected void assertPersistedEmployeeDocumentToMatchUpdatableProperties(EmployeeDocument expectedEmployeeDocument) {
        assertEmployeeDocumentAllUpdatablePropertiesEquals(
            expectedEmployeeDocument,
            getPersistedEmployeeDocument(expectedEmployeeDocument)
        );
    }
}
