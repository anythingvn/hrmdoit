package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.EmploymentContractAsserts.*;
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
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.SalaryBasis;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.EmploymentContractService;
import vn.doit.hrm.service.dto.EmploymentContractDTO;
import vn.doit.hrm.service.mapper.EmploymentContractMapper;

/**
 * Integration tests for the {@link EmploymentContractResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class EmploymentContractResourceIT {

    private static final String DEFAULT_CONTRACT_NO = "AAAAAAAAAA";
    private static final String UPDATED_CONTRACT_NO = "BBBBBBBBBB";

    private static final ContractType DEFAULT_TYPE = ContractType.HDDT;
    private static final ContractType UPDATED_TYPE = ContractType.HDTV;

    private static final Integer DEFAULT_TERM_MONTHS = 1;
    private static final Integer UPDATED_TERM_MONTHS = 2;

    private static final LocalDate DEFAULT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_START_DATE = LocalDate.parse("2026-10-19");

    private static final LocalDate DEFAULT_END_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_END_DATE = LocalDate.parse("2026-10-19");

    private static final String DEFAULT_POSITION = "AAAAAAAAAA";
    private static final String UPDATED_POSITION = "BBBBBBBBBB";

    private static final Long DEFAULT_SALARY = 1L;
    private static final Long UPDATED_SALARY = 2L;

    private static final SalaryBasis DEFAULT_SALARY_BASIS = SalaryBasis.NET;
    private static final SalaryBasis UPDATED_SALARY_BASIS = SalaryBasis.GROSS;

    private static final Long DEFAULT_GROSS_AMOUNT = 1L;
    private static final Long UPDATED_GROSS_AMOUNT = 2L;

    private static final String DEFAULT_RAISE_POLICY = "AAAAAAAAAA";
    private static final String UPDATED_RAISE_POLICY = "BBBBBBBBBB";

    private static final ContractStatus DEFAULT_STATUS = ContractStatus.NHAP;
    private static final ContractStatus UPDATED_STATUS = ContractStatus.DA_KY;

    private static final Long DEFAULT_SIGNED_FILE_ID = 1L;
    private static final Long UPDATED_SIGNED_FILE_ID = 2L;

    private static final String ENTITY_API_URL = "/api/employment-contracts";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmploymentContractRepository employmentContractRepository;

    @Mock
    private EmploymentContractRepository employmentContractRepositoryMock;

    @Autowired
    private EmploymentContractMapper employmentContractMapper;

    @Mock
    private EmploymentContractService employmentContractServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restEmploymentContractMockMvc;

    private EmploymentContract employmentContract;

    private EmploymentContract insertedEmploymentContract;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static EmploymentContract createEntity(EntityManager em) {
        EmploymentContract employmentContract = new EmploymentContract()
            .contractNo(DEFAULT_CONTRACT_NO)
            .type(DEFAULT_TYPE)
            .termMonths(DEFAULT_TERM_MONTHS)
            .startDate(DEFAULT_START_DATE)
            .endDate(DEFAULT_END_DATE)
            .position(DEFAULT_POSITION)
            .salary(DEFAULT_SALARY)
            .salaryBasis(DEFAULT_SALARY_BASIS)
            .grossAmount(DEFAULT_GROSS_AMOUNT)
            .raisePolicy(DEFAULT_RAISE_POLICY)
            .status(DEFAULT_STATUS)
            .signedFileId(DEFAULT_SIGNED_FILE_ID);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        employmentContract.setEmployee(employee);
        return employmentContract;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static EmploymentContract createUpdatedEntity(EntityManager em) {
        EmploymentContract updatedEmploymentContract = new EmploymentContract()
            .contractNo(UPDATED_CONTRACT_NO)
            .type(UPDATED_TYPE)
            .termMonths(UPDATED_TERM_MONTHS)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .position(UPDATED_POSITION)
            .salary(UPDATED_SALARY)
            .salaryBasis(UPDATED_SALARY_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .raisePolicy(UPDATED_RAISE_POLICY)
            .status(UPDATED_STATUS)
            .signedFileId(UPDATED_SIGNED_FILE_ID);
        // Add required entity
        Employee employee;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employee = EmployeeResourceIT.createUpdatedEntity();
            em.persist(employee);
            em.flush();
        } else {
            employee = TestUtil.findAll(em, Employee.class).getFirst();
        }
        updatedEmploymentContract.setEmployee(employee);
        return updatedEmploymentContract;
    }

    @BeforeEach
    void initTest() {
        employmentContract = createEntity(em);
    }

    @AfterEach
    void cleanup() {
        if (insertedEmploymentContract != null) {
            employmentContractRepository.delete(insertedEmploymentContract);
            insertedEmploymentContract = null;
        }
    }

    @Test
    @Transactional
    void createEmploymentContract() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);
        var returnedEmploymentContractDTO = om.readValue(
            restEmploymentContractMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            EmploymentContractDTO.class
        );

        // Validate the EmploymentContract in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedEmploymentContract = employmentContractMapper.toEntity(returnedEmploymentContractDTO);
        assertEmploymentContractUpdatableFieldsEquals(
            returnedEmploymentContract,
            getPersistedEmploymentContract(returnedEmploymentContract)
        );

        insertedEmploymentContract = returnedEmploymentContract;
    }

    @Test
    @Transactional
    void createEmploymentContractWithExistingId() throws Exception {
        // Create the EmploymentContract with an existing ID
        employmentContract.setId(1L);
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restEmploymentContractMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isBadRequest());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employmentContract.setType(null);

        // Create the EmploymentContract, which fails.
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        restEmploymentContractMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStartDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employmentContract.setStartDate(null);

        // Create the EmploymentContract, which fails.
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        restEmploymentContractMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employmentContract.setStatus(null);

        // Create the EmploymentContract, which fails.
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        restEmploymentContractMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllEmploymentContracts() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        // Get all the employmentContractList
        restEmploymentContractMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(employmentContract.getId().intValue())))
            .andExpect(jsonPath("$.[*].contractNo").value(hasItem(DEFAULT_CONTRACT_NO)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].termMonths").value(hasItem(DEFAULT_TERM_MONTHS)))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].position").value(hasItem(DEFAULT_POSITION)))
            .andExpect(jsonPath("$.[*].salary").value(hasItem(DEFAULT_SALARY.intValue())))
            .andExpect(jsonPath("$.[*].salaryBasis").value(hasItem(DEFAULT_SALARY_BASIS.toString())))
            .andExpect(jsonPath("$.[*].grossAmount").value(hasItem(DEFAULT_GROSS_AMOUNT.intValue())))
            .andExpect(jsonPath("$.[*].raisePolicy").value(hasItem(DEFAULT_RAISE_POLICY)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].signedFileId").value(hasItem(DEFAULT_SIGNED_FILE_ID.intValue())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmploymentContractsWithEagerRelationshipsIsEnabled() throws Exception {
        when(employmentContractServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restEmploymentContractMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(employmentContractServiceMock, times(1)).findAllWithEagerRelationships();
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmploymentContractsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(employmentContractServiceMock.findAllWithEagerRelationships()).thenReturn(new ArrayList<>());

        restEmploymentContractMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(employmentContractRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getEmploymentContract() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        // Get the employmentContract
        restEmploymentContractMockMvc
            .perform(get(ENTITY_API_URL_ID, employmentContract.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(employmentContract.getId().intValue()))
            .andExpect(jsonPath("$.contractNo").value(DEFAULT_CONTRACT_NO))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
            .andExpect(jsonPath("$.termMonths").value(DEFAULT_TERM_MONTHS))
            .andExpect(jsonPath("$.startDate").value(DEFAULT_START_DATE.toString()))
            .andExpect(jsonPath("$.endDate").value(DEFAULT_END_DATE.toString()))
            .andExpect(jsonPath("$.position").value(DEFAULT_POSITION))
            .andExpect(jsonPath("$.salary").value(DEFAULT_SALARY.intValue()))
            .andExpect(jsonPath("$.salaryBasis").value(DEFAULT_SALARY_BASIS.toString()))
            .andExpect(jsonPath("$.grossAmount").value(DEFAULT_GROSS_AMOUNT.intValue()))
            .andExpect(jsonPath("$.raisePolicy").value(DEFAULT_RAISE_POLICY))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.signedFileId").value(DEFAULT_SIGNED_FILE_ID.intValue()));
    }

    @Test
    @Transactional
    void getNonExistingEmploymentContract() throws Exception {
        // Get the employmentContract
        restEmploymentContractMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingEmploymentContract() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employmentContract
        EmploymentContract updatedEmploymentContract = employmentContractRepository.findById(employmentContract.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedEmploymentContract are not directly saved in db
        em.detach(updatedEmploymentContract);
        updatedEmploymentContract
            .contractNo(UPDATED_CONTRACT_NO)
            .type(UPDATED_TYPE)
            .termMonths(UPDATED_TERM_MONTHS)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .position(UPDATED_POSITION)
            .salary(UPDATED_SALARY)
            .salaryBasis(UPDATED_SALARY_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .raisePolicy(UPDATED_RAISE_POLICY)
            .status(UPDATED_STATUS)
            .signedFileId(UPDATED_SIGNED_FILE_ID);
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(updatedEmploymentContract);

        restEmploymentContractMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employmentContractDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employmentContractDTO))
            )
            .andExpect(status().isOk());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        // Bản đã ký chỉ gắn qua "Tải bản đã ký lên": giá trị gửi lên bị bỏ qua.
        updatedEmploymentContract.setSignedFileId(DEFAULT_SIGNED_FILE_ID);
        assertPersistedEmploymentContractToMatchAllProperties(updatedEmploymentContract);
    }

    @Test
    @Transactional
    void putNonExistingEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employmentContractDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employmentContractDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employmentContractDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateEmploymentContractWithPatch() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employmentContract using partial update
        EmploymentContract partialUpdatedEmploymentContract = new EmploymentContract();
        partialUpdatedEmploymentContract.setId(employmentContract.getId());

        partialUpdatedEmploymentContract
            .endDate(UPDATED_END_DATE)
            .position(UPDATED_POSITION)
            .salaryBasis(UPDATED_SALARY_BASIS)
            .status(UPDATED_STATUS);

        restEmploymentContractMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmploymentContract.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmploymentContract))
            )
            .andExpect(status().isOk());

        // Validate the EmploymentContract in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertEmploymentContractUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedEmploymentContract, employmentContract),
            getPersistedEmploymentContract(employmentContract)
        );
    }

    @Test
    @Transactional
    void fullUpdateEmploymentContractWithPatch() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employmentContract using partial update
        EmploymentContract partialUpdatedEmploymentContract = new EmploymentContract();
        partialUpdatedEmploymentContract.setId(employmentContract.getId());

        partialUpdatedEmploymentContract
            .contractNo(UPDATED_CONTRACT_NO)
            .type(UPDATED_TYPE)
            .termMonths(UPDATED_TERM_MONTHS)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .position(UPDATED_POSITION)
            .salary(UPDATED_SALARY)
            .salaryBasis(UPDATED_SALARY_BASIS)
            .grossAmount(UPDATED_GROSS_AMOUNT)
            .raisePolicy(UPDATED_RAISE_POLICY)
            .status(UPDATED_STATUS)
            .signedFileId(UPDATED_SIGNED_FILE_ID);

        restEmploymentContractMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmploymentContract.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmploymentContract))
            )
            .andExpect(status().isOk());

        // Validate the EmploymentContract in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        // Bản đã ký chỉ gắn qua "Tải bản đã ký lên": giá trị gửi lên bị bỏ qua.
        partialUpdatedEmploymentContract.setSignedFileId(DEFAULT_SIGNED_FILE_ID);
        assertEmploymentContractUpdatableFieldsEquals(
            partialUpdatedEmploymentContract,
            getPersistedEmploymentContract(partialUpdatedEmploymentContract)
        );
    }

    @Test
    @Transactional
    void patchNonExistingEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, employmentContractDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employmentContractDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employmentContractDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamEmploymentContract() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employmentContract.setId(longCount.incrementAndGet());

        // Create the EmploymentContract
        EmploymentContractDTO employmentContractDTO = employmentContractMapper.toDto(employmentContract);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmploymentContractMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(employmentContractDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the EmploymentContract in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteEmploymentContract() throws Exception {
        // Initialize the database
        insertedEmploymentContract = employmentContractRepository.saveAndFlush(employmentContract);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the employmentContract
        restEmploymentContractMockMvc
            .perform(delete(ENTITY_API_URL_ID, employmentContract.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return employmentContractRepository.count();
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

    protected EmploymentContract getPersistedEmploymentContract(EmploymentContract employmentContract) {
        return employmentContractRepository.findById(employmentContract.getId()).orElseThrow();
    }

    protected void assertPersistedEmploymentContractToMatchAllProperties(EmploymentContract expectedEmploymentContract) {
        assertEmploymentContractAllPropertiesEquals(expectedEmploymentContract, getPersistedEmploymentContract(expectedEmploymentContract));
    }

    protected void assertPersistedEmploymentContractToMatchUpdatableProperties(EmploymentContract expectedEmploymentContract) {
        assertEmploymentContractAllUpdatablePropertiesEquals(
            expectedEmploymentContract,
            getPersistedEmploymentContract(expectedEmploymentContract)
        );
    }
}
