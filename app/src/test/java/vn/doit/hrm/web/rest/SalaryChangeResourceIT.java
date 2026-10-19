package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.SalaryChangeAsserts.*;
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
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.SalaryChangeRepository;
import vn.doit.hrm.service.SalaryChangeService;
import vn.doit.hrm.service.dto.SalaryChangeDTO;
import vn.doit.hrm.service.mapper.SalaryChangeMapper;

/**
 * Integration tests for the {@link SalaryChangeResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class SalaryChangeResourceIT {

    private static final LocalDate DEFAULT_EFFECTIVE_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_EFFECTIVE_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final Long DEFAULT_AMOUNT = 1L;
    private static final Long UPDATED_AMOUNT = 2L;

    private static final SalaryBasis DEFAULT_BASIS = SalaryBasis.NET;
    private static final SalaryBasis UPDATED_BASIS = SalaryBasis.GROSS;

    private static final Long DEFAULT_GROSS_AMOUNT = 1L;
    private static final Long UPDATED_GROSS_AMOUNT = 2L;

    private static final Integer DEFAULT_REVIEW_CYCLE_MONTHS = 1;
    private static final Integer UPDATED_REVIEW_CYCLE_MONTHS = 2;

    private static final LocalDate DEFAULT_NEXT_REVIEW_OVERRIDE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_NEXT_REVIEW_OVERRIDE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/salary-changes";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SalaryChangeRepository salaryChangeRepository;

    @Mock
    private SalaryChangeRepository salaryChangeRepositoryMock;

    @Autowired
    private SalaryChangeMapper salaryChangeMapper;

    @Mock
    private SalaryChangeService salaryChangeServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restSalaryChangeMockMvc;

    private SalaryChange salaryChange;

    private SalaryChange insertedSalaryChange;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SalaryChange createEntity(EntityManager em) {
        SalaryChange salaryChange = new SalaryChange()
            .effectiveDate(DEFAULT_EFFECTIVE_DATE)
            .amount(DEFAULT_AMOUNT)
            .basis(DEFAULT_BASIS)
            .grossAmount(DEFAULT_GROSS_AMOUNT)
            .reviewCycleMonths(DEFAULT_REVIEW_CYCLE_MONTHS)
            .nextReviewOverride(DEFAULT_NEXT_REVIEW_OVERRIDE)
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
        salaryChange.setEmployee(employee);
        return salaryChange;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SalaryChange createUpdatedEntity(EntityManager em) {
        SalaryChange updatedSalaryChange = new SalaryChange()
            .effectiveDate(UPDATED_EFFECTIVE_DATE)
            .amount(UPDATED_AMOUNT)
            .basis(UPDATED_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .reviewCycleMonths(UPDATED_REVIEW_CYCLE_MONTHS)
            .nextReviewOverride(UPDATED_NEXT_REVIEW_OVERRIDE)
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
        updatedSalaryChange.setEmployee(employee);
        return updatedSalaryChange;
    }

    @BeforeEach
    public void initTest() {
        salaryChange = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedSalaryChange != null) {
            salaryChangeRepository.delete(insertedSalaryChange);
            insertedSalaryChange = null;
        }
    }

    @Test
    @Transactional
    void createSalaryChange() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);
        var returnedSalaryChangeDTO = om.readValue(
            restSalaryChangeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            SalaryChangeDTO.class
        );

        // Validate the SalaryChange in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedSalaryChange = salaryChangeMapper.toEntity(returnedSalaryChangeDTO);
        assertSalaryChangeUpdatableFieldsEquals(returnedSalaryChange, getPersistedSalaryChange(returnedSalaryChange));

        insertedSalaryChange = returnedSalaryChange;
    }

    @Test
    @Transactional
    void createSalaryChangeWithExistingId() throws Exception {
        // Create the SalaryChange with an existing ID
        salaryChange.setId(1L);
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restSalaryChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkEffectiveDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        salaryChange.setEffectiveDate(null);

        // Create the SalaryChange, which fails.
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        restSalaryChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkAmountIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        salaryChange.setAmount(null);

        // Create the SalaryChange, which fails.
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        restSalaryChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkBasisIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        salaryChange.setBasis(null);

        // Create the SalaryChange, which fails.
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        restSalaryChangeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllSalaryChanges() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        // Get all the salaryChangeList
        restSalaryChangeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(salaryChange.getId().intValue())))
            .andExpect(jsonPath("$.[*].effectiveDate").value(hasItem(DEFAULT_EFFECTIVE_DATE.toString())))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(DEFAULT_AMOUNT.intValue())))
            .andExpect(jsonPath("$.[*].basis").value(hasItem(DEFAULT_BASIS.toString())))
            .andExpect(jsonPath("$.[*].grossAmount").value(hasItem(DEFAULT_GROSS_AMOUNT.intValue())))
            .andExpect(jsonPath("$.[*].reviewCycleMonths").value(hasItem(DEFAULT_REVIEW_CYCLE_MONTHS)))
            .andExpect(jsonPath("$.[*].nextReviewOverride").value(hasItem(DEFAULT_NEXT_REVIEW_OVERRIDE.toString())))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllSalaryChangesWithEagerRelationshipsIsEnabled() throws Exception {
        when(salaryChangeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restSalaryChangeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(salaryChangeServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllSalaryChangesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(salaryChangeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restSalaryChangeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(salaryChangeRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getSalaryChange() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        // Get the salaryChange
        restSalaryChangeMockMvc
            .perform(get(ENTITY_API_URL_ID, salaryChange.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(salaryChange.getId().intValue()))
            .andExpect(jsonPath("$.effectiveDate").value(DEFAULT_EFFECTIVE_DATE.toString()))
            .andExpect(jsonPath("$.amount").value(DEFAULT_AMOUNT.intValue()))
            .andExpect(jsonPath("$.basis").value(DEFAULT_BASIS.toString()))
            .andExpect(jsonPath("$.grossAmount").value(DEFAULT_GROSS_AMOUNT.intValue()))
            .andExpect(jsonPath("$.reviewCycleMonths").value(DEFAULT_REVIEW_CYCLE_MONTHS))
            .andExpect(jsonPath("$.nextReviewOverride").value(DEFAULT_NEXT_REVIEW_OVERRIDE.toString()))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE));
    }

    @Test
    @Transactional
    void getNonExistingSalaryChange() throws Exception {
        // Get the salaryChange
        restSalaryChangeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingSalaryChange() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the salaryChange
        SalaryChange updatedSalaryChange = salaryChangeRepository.findById(salaryChange.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedSalaryChange are not directly saved in db
        em.detach(updatedSalaryChange);
        updatedSalaryChange
            .effectiveDate(UPDATED_EFFECTIVE_DATE)
            .amount(UPDATED_AMOUNT)
            .basis(UPDATED_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .reviewCycleMonths(UPDATED_REVIEW_CYCLE_MONTHS)
            .nextReviewOverride(UPDATED_NEXT_REVIEW_OVERRIDE)
            .note(UPDATED_NOTE);
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(updatedSalaryChange);

        restSalaryChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, salaryChangeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(salaryChangeDTO))
            )
            .andExpect(status().isOk());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedSalaryChangeToMatchAllProperties(updatedSalaryChange);
    }

    @Test
    @Transactional
    void putNonExistingSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, salaryChangeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(salaryChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(salaryChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateSalaryChangeWithPatch() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the salaryChange using partial update
        SalaryChange partialUpdatedSalaryChange = new SalaryChange();
        partialUpdatedSalaryChange.setId(salaryChange.getId());

        partialUpdatedSalaryChange
            .effectiveDate(UPDATED_EFFECTIVE_DATE)
            .amount(UPDATED_AMOUNT)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .reviewCycleMonths(UPDATED_REVIEW_CYCLE_MONTHS)
            .nextReviewOverride(UPDATED_NEXT_REVIEW_OVERRIDE)
            .note(UPDATED_NOTE);

        restSalaryChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSalaryChange.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSalaryChange))
            )
            .andExpect(status().isOk());

        // Validate the SalaryChange in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSalaryChangeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedSalaryChange, salaryChange),
            getPersistedSalaryChange(salaryChange)
        );
    }

    @Test
    @Transactional
    void fullUpdateSalaryChangeWithPatch() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the salaryChange using partial update
        SalaryChange partialUpdatedSalaryChange = new SalaryChange();
        partialUpdatedSalaryChange.setId(salaryChange.getId());

        partialUpdatedSalaryChange
            .effectiveDate(UPDATED_EFFECTIVE_DATE)
            .amount(UPDATED_AMOUNT)
            .basis(UPDATED_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .reviewCycleMonths(UPDATED_REVIEW_CYCLE_MONTHS)
            .nextReviewOverride(UPDATED_NEXT_REVIEW_OVERRIDE)
            .note(UPDATED_NOTE);

        restSalaryChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSalaryChange.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSalaryChange))
            )
            .andExpect(status().isOk());

        // Validate the SalaryChange in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSalaryChangeUpdatableFieldsEquals(partialUpdatedSalaryChange, getPersistedSalaryChange(partialUpdatedSalaryChange));
    }

    @Test
    @Transactional
    void patchNonExistingSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, salaryChangeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(salaryChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(salaryChangeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamSalaryChange() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        salaryChange.setId(longCount.incrementAndGet());

        // Create the SalaryChange
        SalaryChangeDTO salaryChangeDTO = salaryChangeMapper.toDto(salaryChange);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSalaryChangeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(salaryChangeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SalaryChange in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteSalaryChange() throws Exception {
        // Initialize the database
        insertedSalaryChange = salaryChangeRepository.saveAndFlush(salaryChange);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the salaryChange
        restSalaryChangeMockMvc
            .perform(delete(ENTITY_API_URL_ID, salaryChange.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return salaryChangeRepository.count();
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

    protected SalaryChange getPersistedSalaryChange(SalaryChange salaryChange) {
        return salaryChangeRepository.findById(salaryChange.getId()).orElseThrow();
    }

    protected void assertPersistedSalaryChangeToMatchAllProperties(SalaryChange expectedSalaryChange) {
        assertSalaryChangeAllPropertiesEquals(expectedSalaryChange, getPersistedSalaryChange(expectedSalaryChange));
    }

    protected void assertPersistedSalaryChangeToMatchUpdatableProperties(SalaryChange expectedSalaryChange) {
        assertSalaryChangeAllUpdatablePropertiesEquals(expectedSalaryChange, getPersistedSalaryChange(expectedSalaryChange));
    }
}
