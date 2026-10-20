package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.WorkAssignmentAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
    private static final LocalDate UPDATED_FROM_DATE = LocalDate.parse("2026-10-19");

    private static final LocalDate DEFAULT_TO_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_TO_DATE = LocalDate.parse("2026-10-19");

    private static final String DEFAULT_DECISION_NO = "AAAAAAAAAA";
    private static final String UPDATED_DECISION_NO = "BBBBBBBBBB";

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/work-assignments";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

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
            employee = TestUtil.findAll(em, Employee.class).getFirst();
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
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        updatedWorkAssignment.setEmployee(employee);
        return updatedWorkAssignment;
    }

    @BeforeEach
    void initTest() {
        workAssignment = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedWorkAssignment != null) {
            workAssignmentRepository.delete(insertedWorkAssignment);
            insertedWorkAssignment = null;
        }
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
        when(workAssignmentServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restWorkAssignmentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(workAssignmentServiceMock, times(1)).findAllWithEagerRelationships();
    }

    @SuppressWarnings({ "unchecked" })
    void getAllWorkAssignmentsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(workAssignmentServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

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
