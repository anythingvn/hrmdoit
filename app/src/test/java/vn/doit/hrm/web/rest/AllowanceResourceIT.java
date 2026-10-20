package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.AllowanceAsserts.*;
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
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.repository.AllowanceRepository;
import vn.doit.hrm.service.AllowanceService;
import vn.doit.hrm.service.dto.AllowanceDTO;
import vn.doit.hrm.service.mapper.AllowanceMapper;

/**
 * Integration tests for the {@link AllowanceResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class AllowanceResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final Long DEFAULT_AMOUNT_PER_MONTH = 1L;
    private static final Long UPDATED_AMOUNT_PER_MONTH = 2L;

    private static final LocalDate DEFAULT_FROM_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_FROM_DATE = LocalDate.parse("2026-10-19");

    private static final LocalDate DEFAULT_TO_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_TO_DATE = LocalDate.parse("2026-10-19");

    private static final String ENTITY_API_URL = "/api/allowances";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AllowanceRepository allowanceRepository;

    @Mock
    private AllowanceRepository allowanceRepositoryMock;

    @Autowired
    private AllowanceMapper allowanceMapper;

    @Mock
    private AllowanceService allowanceServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restAllowanceMockMvc;

    private Allowance allowance;

    private Allowance insertedAllowance;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Allowance createEntity(EntityManager em) {
        Allowance allowance = new Allowance()
            .name(DEFAULT_NAME)
            .amountPerMonth(DEFAULT_AMOUNT_PER_MONTH)
            .fromDate(DEFAULT_FROM_DATE)
            .toDate(DEFAULT_TO_DATE);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        allowance.setEmployee(employee);
        return allowance;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Allowance createUpdatedEntity(EntityManager em) {
        Allowance updatedAllowance = new Allowance()
            .name(UPDATED_NAME)
            .amountPerMonth(UPDATED_AMOUNT_PER_MONTH)
            .fromDate(UPDATED_FROM_DATE)
            .toDate(UPDATED_TO_DATE);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createUpdatedEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        updatedAllowance.setEmployee(employee);
        return updatedAllowance;
    }

    @BeforeEach
    void initTest() {
        allowance = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedAllowance != null) {
            allowanceRepository.delete(insertedAllowance);
            insertedAllowance = null;
        }
    }

    @Test
    @Transactional
    void createAllowance() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);
        var returnedAllowanceDTO = om.readValue(
            restAllowanceMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AllowanceDTO.class
        );

        // Validate the Allowance in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedAllowance = allowanceMapper.toEntity(returnedAllowanceDTO);
        assertAllowanceUpdatableFieldsEquals(returnedAllowance, getPersistedAllowance(returnedAllowance));

        insertedAllowance = returnedAllowance;
    }

    @Test
    @Transactional
    void createAllowanceWithExistingId() throws Exception {
        // Create the Allowance with an existing ID
        allowance.setId(1L);
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAllowanceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        allowance.setName(null);

        // Create the Allowance, which fails.
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        restAllowanceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAmountPerMonthIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        allowance.setAmountPerMonth(null);

        // Create the Allowance, which fails.
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        restAllowanceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFromDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        allowance.setFromDate(null);

        // Create the Allowance, which fails.
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        restAllowanceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllAllowances() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        // Get all the allowanceList
        restAllowanceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(allowance.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].amountPerMonth").value(hasItem(DEFAULT_AMOUNT_PER_MONTH.intValue())))
            .andExpect(jsonPath("$.[*].fromDate").value(hasItem(DEFAULT_FROM_DATE.toString())))
            .andExpect(jsonPath("$.[*].toDate").value(hasItem(DEFAULT_TO_DATE.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllAllowancesWithEagerRelationshipsIsEnabled() throws Exception {
        when(allowanceServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restAllowanceMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(allowanceServiceMock, times(1)).findAllWithEagerRelationships();
    }

    @SuppressWarnings({ "unchecked" })
    void getAllAllowancesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(allowanceServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restAllowanceMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(allowanceRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getAllowance() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        // Get the allowance
        restAllowanceMockMvc
            .perform(get(ENTITY_API_URL_ID, allowance.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(allowance.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.amountPerMonth").value(DEFAULT_AMOUNT_PER_MONTH.intValue()))
            .andExpect(jsonPath("$.fromDate").value(DEFAULT_FROM_DATE.toString()))
            .andExpect(jsonPath("$.toDate").value(DEFAULT_TO_DATE.toString()));
    }

    @Test
    @Transactional
    void getNonExistingAllowance() throws Exception {
        // Get the allowance
        restAllowanceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingAllowance() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the allowance
        Allowance updatedAllowance = allowanceRepository.findById(allowance.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedAllowance are not directly saved in db
        em.detach(updatedAllowance);
        updatedAllowance.name(UPDATED_NAME).amountPerMonth(UPDATED_AMOUNT_PER_MONTH).fromDate(UPDATED_FROM_DATE).toDate(UPDATED_TO_DATE);
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(updatedAllowance);

        restAllowanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, allowanceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(allowanceDTO))
            )
            .andExpect(status().isOk());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAllowanceToMatchAllProperties(updatedAllowance);
    }

    @Test
    @Transactional
    void putNonExistingAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, allowanceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(allowanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(allowanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateAllowanceWithPatch() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the allowance using partial update
        Allowance partialUpdatedAllowance = new Allowance();
        partialUpdatedAllowance.setId(allowance.getId());

        partialUpdatedAllowance.amountPerMonth(UPDATED_AMOUNT_PER_MONTH).toDate(UPDATED_TO_DATE);

        restAllowanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAllowance.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAllowance))
            )
            .andExpect(status().isOk());

        // Validate the Allowance in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAllowanceUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedAllowance, allowance),
            getPersistedAllowance(allowance)
        );
    }

    @Test
    @Transactional
    void fullUpdateAllowanceWithPatch() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the allowance using partial update
        Allowance partialUpdatedAllowance = new Allowance();
        partialUpdatedAllowance.setId(allowance.getId());

        partialUpdatedAllowance
            .name(UPDATED_NAME)
            .amountPerMonth(UPDATED_AMOUNT_PER_MONTH)
            .fromDate(UPDATED_FROM_DATE)
            .toDate(UPDATED_TO_DATE);

        restAllowanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAllowance.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAllowance))
            )
            .andExpect(status().isOk());

        // Validate the Allowance in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAllowanceUpdatableFieldsEquals(partialUpdatedAllowance, getPersistedAllowance(partialUpdatedAllowance));
    }

    @Test
    @Transactional
    void patchNonExistingAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, allowanceDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(allowanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(allowanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamAllowance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        allowance.setId(longCount.incrementAndGet());

        // Create the Allowance
        AllowanceDTO allowanceDTO = allowanceMapper.toDto(allowance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAllowanceMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(allowanceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Allowance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteAllowance() throws Exception {
        // Initialize the database
        insertedAllowance = allowanceRepository.saveAndFlush(allowance);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the allowance
        restAllowanceMockMvc
            .perform(delete(ENTITY_API_URL_ID, allowance.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return allowanceRepository.count();
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

    protected Allowance getPersistedAllowance(Allowance allowance) {
        return allowanceRepository.findById(allowance.getId()).orElseThrow();
    }

    protected void assertPersistedAllowanceToMatchAllProperties(Allowance expectedAllowance) {
        assertAllowanceAllPropertiesEquals(expectedAllowance, getPersistedAllowance(expectedAllowance));
    }

    protected void assertPersistedAllowanceToMatchUpdatableProperties(Allowance expectedAllowance) {
        assertAllowanceAllUpdatablePropertiesEquals(expectedAllowance, getPersistedAllowance(expectedAllowance));
    }
}
