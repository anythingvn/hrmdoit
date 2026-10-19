package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.WorkAssignmentAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
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
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.service.WorkAssignmentService;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.service.mapper.WorkAssignmentMapper;

/**
 * Integration tests for the {@link WorkAssignmentResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class WorkAssignmentResourceIT {

    private static final WorkLocationType DEFAULT_TYPE = WorkLocationType.VAN_PHONG;
    private static final WorkLocationType UPDATED_TYPE = WorkLocationType.KHACH_HANG;

    private static final String DEFAULT_UNIT = "AAAAAAAAAA";
    private static final String UPDATED_UNIT = "BBBBBBBBBB";

    private static final String DEFAULT_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_ADDRESS = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_FROM_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_FROM_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_TO_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_TO_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_DECISION_NO = "AAAAAAAAAA";
    private static final String UPDATED_DECISION_NO = "BBBBBBBBBB";

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/work-assignments";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private WorkAssignmentRepository workAssignmentRepository;

    @Mock
    private WorkAssignmentRepository workAssignmentRepositoryMock;

    @Autowired
    private WorkAssignmentMapper workAssignmentMapper;

    @Mock
    private WorkAssignmentService workAssignmentServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restWorkAssignmentMockMvc;

    private WorkAssignment workAssignment;

    private WorkAssignment insertedWorkAssignment;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkAssignment createEntity(EntityManager em) {
        WorkAssignment workAssignment = new WorkAssignment()
            .type(DEFAULT_TYPE)
            .unit(DEFAULT_UNIT)
            .address(DEFAULT_ADDRESS)
            .fromDate(DEFAULT_FROM_DATE)
            .toDate(DEFAULT_TO_DATE)
            .decisionNo(DEFAULT_DECISION_NO)
            .note(DEFAULT_NOTE);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).get(0);
        }
        workAssignment.setEmployee(employee);
        return workAssignment;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkAssignment createUpdatedEntity(EntityManager em) {
        WorkAssignment updatedWorkAssignment = new WorkAssignment()
            .type(UPDATED_TYPE)
            .unit(UPDATED_UNIT)
            .address(UPDATED_ADDRESS)
            .fromDate(UPDATED_FROM_DATE)
            .toDate(UPDATED_TO_DATE)
            .decisionNo(UPDATED_DECISION_NO)
            .note(UPDATED_NOTE);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createUpdatedEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).get(0);
        }
        updatedWorkAssignment.setEmployee(employee);
        return updatedWorkAssignment;
    }

    @BeforeEach
    public void initTest() {
        workAssignment = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedWorkAssignment != null) {
            workAssignmentRepository.delete(insertedWorkAssignment);
            insertedWorkAssignment = null;
        }
    }

    @Test
    @Transactional
    void createWorkAssignment() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);
        var returnedWorkAssignmentDTO = om.readValue(
            restWorkAssignmentMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workAssignmentDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            WorkAssignmentDTO.class
        );

        // Validate the WorkAssignment in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedWorkAssignment = workAssignmentMapper.toEntity(returnedWorkAssignmentDTO);
        assertWorkAssignmentUpdatableFieldsEquals(returnedWorkAssignment, getPersistedWorkAssignment(returnedWorkAssignment));

        insertedWorkAssignment = returnedWorkAssignment;
    }

    @Test
    @Transactional
    void createWorkAssignmentWithExistingId() throws Exception {
        // Create the WorkAssignment with an existing ID
        workAssignment.setId(1L);
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restWorkAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workAssignmentDTO)))
            .andExpect(status().isBadRequest());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workAssignment.setType(null);

        // Create the WorkAssignment, which fails.
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        restWorkAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workAssignmentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFromDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workAssignment.setFromDate(null);

        // Create the WorkAssignment, which fails.
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        restWorkAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workAssignmentDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllWorkAssignments() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        // Get all the workAssignmentList
        restWorkAssignmentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(workAssignment.getId().intValue())))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].unit").value(hasItem(DEFAULT_UNIT)))
            .andExpect(jsonPath("$.[*].address").value(hasItem(DEFAULT_ADDRESS)))
            .andExpect(jsonPath("$.[*].fromDate").value(hasItem(DEFAULT_FROM_DATE.toString())))
            .andExpect(jsonPath("$.[*].toDate").value(hasItem(DEFAULT_TO_DATE.toString())))
            .andExpect(jsonPath("$.[*].decisionNo").value(hasItem(DEFAULT_DECISION_NO)))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllWorkAssignmentsWithEagerRelationshipsIsEnabled() throws Exception {
        when(workAssignmentServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restWorkAssignmentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(workAssignmentServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllWorkAssignmentsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(workAssignmentServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restWorkAssignmentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(workAssignmentRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getWorkAssignment() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        // Get the workAssignment
        restWorkAssignmentMockMvc
            .perform(get(ENTITY_API_URL_ID, workAssignment.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(workAssignment.getId().intValue()))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
            .andExpect(jsonPath("$.unit").value(DEFAULT_UNIT))
            .andExpect(jsonPath("$.address").value(DEFAULT_ADDRESS))
            .andExpect(jsonPath("$.fromDate").value(DEFAULT_FROM_DATE.toString()))
            .andExpect(jsonPath("$.toDate").value(DEFAULT_TO_DATE.toString()))
            .andExpect(jsonPath("$.decisionNo").value(DEFAULT_DECISION_NO))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE));
    }

    @Test
    @Transactional
    void getNonExistingWorkAssignment() throws Exception {
        // Get the workAssignment
        restWorkAssignmentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingWorkAssignment() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workAssignment
        WorkAssignment updatedWorkAssignment = workAssignmentRepository.findById(workAssignment.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedWorkAssignment are not directly saved in db
        em.detach(updatedWorkAssignment);
        updatedWorkAssignment
            .type(UPDATED_TYPE)
            .unit(UPDATED_UNIT)
            .address(UPDATED_ADDRESS)
            .fromDate(UPDATED_FROM_DATE)
            .toDate(UPDATED_TO_DATE)
            .decisionNo(UPDATED_DECISION_NO)
            .note(UPDATED_NOTE);
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(updatedWorkAssignment);

        restWorkAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workAssignmentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workAssignmentDTO))
            )
            .andExpect(status().isOk());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedWorkAssignmentToMatchAllProperties(updatedWorkAssignment);
    }

    @Test
    @Transactional
    void putNonExistingWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workAssignmentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workAssignmentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workAssignmentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workAssignmentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateWorkAssignmentWithPatch() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workAssignment using partial update
        WorkAssignment partialUpdatedWorkAssignment = new WorkAssignment();
        partialUpdatedWorkAssignment.setId(workAssignment.getId());

        partialUpdatedWorkAssignment.type(UPDATED_TYPE).address(UPDATED_ADDRESS).fromDate(UPDATED_FROM_DATE).toDate(UPDATED_TO_DATE);

        restWorkAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkAssignment.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkAssignment))
            )
            .andExpect(status().isOk());

        // Validate the WorkAssignment in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkAssignmentUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedWorkAssignment, workAssignment),
            getPersistedWorkAssignment(workAssignment)
        );
    }

    @Test
    @Transactional
    void fullUpdateWorkAssignmentWithPatch() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workAssignment using partial update
        WorkAssignment partialUpdatedWorkAssignment = new WorkAssignment();
        partialUpdatedWorkAssignment.setId(workAssignment.getId());

        partialUpdatedWorkAssignment
            .type(UPDATED_TYPE)
            .unit(UPDATED_UNIT)
            .address(UPDATED_ADDRESS)
            .fromDate(UPDATED_FROM_DATE)
            .toDate(UPDATED_TO_DATE)
            .decisionNo(UPDATED_DECISION_NO)
            .note(UPDATED_NOTE);

        restWorkAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkAssignment.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkAssignment))
            )
            .andExpect(status().isOk());

        // Validate the WorkAssignment in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkAssignmentUpdatableFieldsEquals(partialUpdatedWorkAssignment, getPersistedWorkAssignment(partialUpdatedWorkAssignment));
    }

    @Test
    @Transactional
    void patchNonExistingWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, workAssignmentDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workAssignmentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workAssignmentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamWorkAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workAssignment.setId(longCount.incrementAndGet());

        // Create the WorkAssignment
        WorkAssignmentDTO workAssignmentDTO = workAssignmentMapper.toDto(workAssignment);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkAssignmentMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(workAssignmentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteWorkAssignment() throws Exception {
        // Initialize the database
        insertedWorkAssignment = workAssignmentRepository.saveAndFlush(workAssignment);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the workAssignment
        restWorkAssignmentMockMvc
            .perform(delete(ENTITY_API_URL_ID, workAssignment.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return workAssignmentRepository.count();
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

    protected WorkAssignment getPersistedWorkAssignment(WorkAssignment workAssignment) {
        return workAssignmentRepository.findById(workAssignment.getId()).orElseThrow();
    }

    protected void assertPersistedWorkAssignmentToMatchAllProperties(WorkAssignment expectedWorkAssignment) {
        assertWorkAssignmentAllPropertiesEquals(expectedWorkAssignment, getPersistedWorkAssignment(expectedWorkAssignment));
    }

    protected void assertPersistedWorkAssignmentToMatchUpdatableProperties(WorkAssignment expectedWorkAssignment) {
        assertWorkAssignmentAllUpdatablePropertiesEquals(expectedWorkAssignment, getPersistedWorkAssignment(expectedWorkAssignment));
    }
}
