package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.EmployeeDocumentAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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
@WithMockUser
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
    private static final Instant UPDATED_UPLOADED_AT = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String ENTITY_API_URL = "/api/employee-documents";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

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
            employee = TestUtil.findAll(em, Employee.class).get(0);
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
            employee = TestUtil.findAll(em, Employee.class).get(0);
        }
        updatedEmployeeDocument.setEmployee(employee);
        return updatedEmployeeDocument;
    }

    @BeforeEach
    public void initTest() {
        employeeDocument = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedEmployeeDocument != null) {
            employeeDocumentRepository.delete(insertedEmployeeDocument);
            insertedEmployeeDocument = null;
        }
    }

    @Test
    @Transactional
    void createEmployeeDocument() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);
        var returnedEmployeeDocumentDTO = om.readValue(
            restEmployeeDocumentMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            EmployeeDocumentDTO.class
        );

        // Validate the EmployeeDocument in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedEmployeeDocument = employeeDocumentMapper.toEntity(returnedEmployeeDocumentDTO);
        assertEmployeeDocumentUpdatableFieldsEquals(returnedEmployeeDocument, getPersistedEmployeeDocument(returnedEmployeeDocument));

        insertedEmployeeDocument = returnedEmployeeDocument;
    }

    @Test
    @Transactional
    void createEmployeeDocumentWithExistingId() throws Exception {
        // Create the EmployeeDocument with an existing ID
        employeeDocument.setId(1L);
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkFileNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employeeDocument.setFileName(null);

        // Create the EmployeeDocument, which fails.
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkContentTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employeeDocument.setContentType(null);

        // Create the EmployeeDocument, which fails.
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkSizeBytesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employeeDocument.setSizeBytes(null);

        // Create the EmployeeDocument, which fails.
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStoragePathIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employeeDocument.setStoragePath(null);

        // Create the EmployeeDocument, which fails.
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkUploadedAtIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employeeDocument.setUploadedAt(null);

        // Create the EmployeeDocument, which fails.
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        restEmployeeDocumentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
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
            .andExpect(jsonPath("$.[*].storagePath").value(hasItem(DEFAULT_STORAGE_PATH)))
            .andExpect(jsonPath("$.[*].uploadedBy").value(hasItem(DEFAULT_UPLOADED_BY)))
            .andExpect(jsonPath("$.[*].uploadedAt").value(hasItem(DEFAULT_UPLOADED_AT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeeDocumentsWithEagerRelationshipsIsEnabled() throws Exception {
        when(employeeDocumentServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restEmployeeDocumentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(employeeDocumentServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeeDocumentsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(employeeDocumentServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

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
            .andExpect(jsonPath("$.storagePath").value(DEFAULT_STORAGE_PATH))
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
    void putExistingEmployeeDocument() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employeeDocument
        EmployeeDocument updatedEmployeeDocument = employeeDocumentRepository.findById(employeeDocument.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedEmployeeDocument are not directly saved in db
        em.detach(updatedEmployeeDocument);
        updatedEmployeeDocument
            .fileName(UPDATED_FILE_NAME)
            .contentType(UPDATED_CONTENT_TYPE)
            .sizeBytes(UPDATED_SIZE_BYTES)
            .storagePath(UPDATED_STORAGE_PATH)
            .uploadedBy(UPDATED_UPLOADED_BY)
            .uploadedAt(UPDATED_UPLOADED_AT);
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(updatedEmployeeDocument);

        restEmployeeDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employeeDocumentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employeeDocumentDTO))
            )
            .andExpect(status().isOk());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedEmployeeDocumentToMatchAllProperties(updatedEmployeeDocument);
    }

    @Test
    @Transactional
    void putNonExistingEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employeeDocumentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employeeDocumentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employeeDocumentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateEmployeeDocumentWithPatch() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employeeDocument using partial update
        EmployeeDocument partialUpdatedEmployeeDocument = new EmployeeDocument();
        partialUpdatedEmployeeDocument.setId(employeeDocument.getId());

        partialUpdatedEmployeeDocument.fileName(UPDATED_FILE_NAME).sizeBytes(UPDATED_SIZE_BYTES).uploadedBy(UPDATED_UPLOADED_BY);

        restEmployeeDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmployeeDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmployeeDocument))
            )
            .andExpect(status().isOk());

        // Validate the EmployeeDocument in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertEmployeeDocumentUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedEmployeeDocument, employeeDocument),
            getPersistedEmployeeDocument(employeeDocument)
        );
    }

    @Test
    @Transactional
    void fullUpdateEmployeeDocumentWithPatch() throws Exception {
        // Initialize the database
        insertedEmployeeDocument = employeeDocumentRepository.saveAndFlush(employeeDocument);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employeeDocument using partial update
        EmployeeDocument partialUpdatedEmployeeDocument = new EmployeeDocument();
        partialUpdatedEmployeeDocument.setId(employeeDocument.getId());

        partialUpdatedEmployeeDocument
            .fileName(UPDATED_FILE_NAME)
            .contentType(UPDATED_CONTENT_TYPE)
            .sizeBytes(UPDATED_SIZE_BYTES)
            .storagePath(UPDATED_STORAGE_PATH)
            .uploadedBy(UPDATED_UPLOADED_BY)
            .uploadedAt(UPDATED_UPLOADED_AT);

        restEmployeeDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmployeeDocument.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmployeeDocument))
            )
            .andExpect(status().isOk());

        // Validate the EmployeeDocument in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertEmployeeDocumentUpdatableFieldsEquals(
            partialUpdatedEmployeeDocument,
            getPersistedEmployeeDocument(partialUpdatedEmployeeDocument)
        );
    }

    @Test
    @Transactional
    void patchNonExistingEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, employeeDocumentDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employeeDocumentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employeeDocumentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamEmployeeDocument() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employeeDocument.setId(longCount.incrementAndGet());

        // Create the EmployeeDocument
        EmployeeDocumentDTO employeeDocumentDTO = employeeDocumentMapper.toDto(employeeDocument);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeDocumentMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(employeeDocumentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the EmployeeDocument in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
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
