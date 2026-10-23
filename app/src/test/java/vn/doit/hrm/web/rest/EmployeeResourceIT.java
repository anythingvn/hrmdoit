package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.EmployeeAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.domain.enumeration.TerminationType;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.service.EmployeeService;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.mapper.EmployeeMapper;

/**
 * Integration tests for the {@link EmployeeResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class EmployeeResourceIT {

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final PersonType DEFAULT_PERSON_TYPE = PersonType.NHAN_VIEN;
    private static final PersonType UPDATED_PERSON_TYPE = PersonType.DICH_VU;

    private static final String DEFAULT_FULL_NAME = "AAAAAAAAAA";
    private static final String UPDATED_FULL_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SEARCH_TEXT = "AAAAAAAAAA";
    private static final String UPDATED_SEARCH_TEXT = "bbbbbbbbbb bbbbbbbbbb bbbbbbbbbb"; // fold(code + tên + email)

    private static final Gender DEFAULT_GENDER = Gender.NAM;
    private static final Gender UPDATED_GENDER = Gender.NU;

    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_BIRTH_DATE = LocalDate.parse("2026-10-19");
    private static final LocalDate SMALLER_BIRTH_DATE = LocalDate.ofEpochDay(-1L);

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_PHONE = "AAAAAAAAAA";
    private static final String UPDATED_PHONE = "BBBBBBBBBB";

    private static final String DEFAULT_RESIDENCE = "AAAAAAAAAA";
    private static final String UPDATED_RESIDENCE = "BBBBBBBBBB";

    private static final String DEFAULT_EDUCATION = "AAAAAAAAAA";
    private static final String UPDATED_EDUCATION = "BBBBBBBBBB";

    private static final String DEFAULT_ID_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_ID_NUMBER = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_ID_ISSUE_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_ID_ISSUE_DATE = LocalDate.parse("2026-10-19");
    private static final LocalDate SMALLER_ID_ISSUE_DATE = LocalDate.ofEpochDay(-1L);

    private static final String DEFAULT_ID_ISSUE_PLACE = "AAAAAAAAAA";
    private static final String UPDATED_ID_ISSUE_PLACE = "BBBBBBBBBB";

    private static final String DEFAULT_TAX_CODE = "AAAAAAAAAA";
    private static final String UPDATED_TAX_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_BANK_ACCOUNT = "AAAAAAAAAA";
    private static final String UPDATED_BANK_ACCOUNT = "BBBBBBBBBB";

    private static final String DEFAULT_BANK_NAME = "AAAAAAAAAA";
    private static final String UPDATED_BANK_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SOCIAL_INSURANCE_NO = "AAAAAAAAAA";
    private static final String UPDATED_SOCIAL_INSURANCE_NO = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_SOCIAL_INSURANCE_START = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_SOCIAL_INSURANCE_START = LocalDate.parse("2026-10-19");
    private static final LocalDate SMALLER_SOCIAL_INSURANCE_START = LocalDate.ofEpochDay(-1L);

    private static final Long DEFAULT_SOCIAL_INSURANCE_SALARY = 1L;
    private static final Long UPDATED_SOCIAL_INSURANCE_SALARY = 2L;
    private static final Long SMALLER_SOCIAL_INSURANCE_SALARY = 1L - 1L;

    private static final Integer DEFAULT_DEPENDANTS = 0;
    private static final Integer UPDATED_DEPENDANTS = 1;
    private static final Integer SMALLER_DEPENDANTS = 0 - 1;

    private static final String DEFAULT_POSITION = "AAAAAAAAAA";
    private static final String UPDATED_POSITION = "BBBBBBBBBB";

    private static final EmploymentStatus DEFAULT_STATUS = EmploymentStatus.DANG_LAM_VIEC;
    private static final EmploymentStatus UPDATED_STATUS = EmploymentStatus.DA_NGHI;

    private static final LocalDate DEFAULT_JOIN_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_JOIN_DATE = LocalDate.parse("2026-10-19");
    private static final LocalDate SMALLER_JOIN_DATE = LocalDate.ofEpochDay(-1L);

    private static final LocalDate DEFAULT_LAST_WORKING_DAY = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_LAST_WORKING_DAY = LocalDate.parse("2026-10-19");
    private static final LocalDate SMALLER_LAST_WORKING_DAY = LocalDate.ofEpochDay(-1L);

    private static final TerminationType DEFAULT_TERMINATION_TYPE = TerminationType.TU_NGHI;
    private static final TerminationType UPDATED_TERMINATION_TYPE = TerminationType.HET_HAN_HD;

    private static final String DEFAULT_TERMINATION_REASON = "AAAAAAAAAA";
    private static final String UPDATED_TERMINATION_REASON = "BBBBBBBBBB";

    private static final String DEFAULT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_NOTE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/employees";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeRepository employeeRepositoryMock;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Mock
    private EmployeeService employeeServiceMock;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restEmployeeMockMvc;

    private Employee employee;

    private Employee insertedEmployee;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Employee createEntity() {
        return new Employee()
            .code(DEFAULT_CODE)
            .personType(DEFAULT_PERSON_TYPE)
            .fullName(DEFAULT_FULL_NAME)
            .searchText(DEFAULT_SEARCH_TEXT)
            .gender(DEFAULT_GENDER)
            .birthDate(DEFAULT_BIRTH_DATE)
            .email(DEFAULT_EMAIL)
            .phone(DEFAULT_PHONE)
            .residence(DEFAULT_RESIDENCE)
            .education(DEFAULT_EDUCATION)
            .idNumber(DEFAULT_ID_NUMBER)
            .idIssueDate(DEFAULT_ID_ISSUE_DATE)
            .idIssuePlace(DEFAULT_ID_ISSUE_PLACE)
            .taxCode(DEFAULT_TAX_CODE)
            .bankAccount(DEFAULT_BANK_ACCOUNT)
            .bankName(DEFAULT_BANK_NAME)
            .socialInsuranceNo(DEFAULT_SOCIAL_INSURANCE_NO)
            .socialInsuranceStart(DEFAULT_SOCIAL_INSURANCE_START)
            .socialInsuranceSalary(DEFAULT_SOCIAL_INSURANCE_SALARY)
            .dependants(DEFAULT_DEPENDANTS)
            .position(DEFAULT_POSITION)
            .status(DEFAULT_STATUS)
            .joinDate(DEFAULT_JOIN_DATE)
            .lastWorkingDay(DEFAULT_LAST_WORKING_DAY)
            .terminationType(DEFAULT_TERMINATION_TYPE)
            .terminationReason(DEFAULT_TERMINATION_REASON)
            .note(DEFAULT_NOTE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Employee createUpdatedEntity() {
        return new Employee()
            .code(UPDATED_CODE)
            .personType(UPDATED_PERSON_TYPE)
            .fullName(UPDATED_FULL_NAME)
            .searchText(UPDATED_SEARCH_TEXT)
            .gender(UPDATED_GENDER)
            .birthDate(UPDATED_BIRTH_DATE)
            .email(UPDATED_EMAIL)
            .phone(UPDATED_PHONE)
            .residence(UPDATED_RESIDENCE)
            .education(UPDATED_EDUCATION)
            .idNumber(UPDATED_ID_NUMBER)
            .idIssueDate(UPDATED_ID_ISSUE_DATE)
            .idIssuePlace(UPDATED_ID_ISSUE_PLACE)
            .taxCode(UPDATED_TAX_CODE)
            .bankAccount(UPDATED_BANK_ACCOUNT)
            .bankName(UPDATED_BANK_NAME)
            .socialInsuranceNo(UPDATED_SOCIAL_INSURANCE_NO)
            .socialInsuranceStart(UPDATED_SOCIAL_INSURANCE_START)
            .socialInsuranceSalary(UPDATED_SOCIAL_INSURANCE_SALARY)
            .dependants(UPDATED_DEPENDANTS)
            .position(UPDATED_POSITION)
            .status(UPDATED_STATUS)
            .joinDate(UPDATED_JOIN_DATE)
            .lastWorkingDay(UPDATED_LAST_WORKING_DAY)
            .terminationType(UPDATED_TERMINATION_TYPE)
            .terminationReason(UPDATED_TERMINATION_REASON)
            .note(UPDATED_NOTE);
    }

    @BeforeEach
    void initTest() {
        employee = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedEmployee != null) {
            employeeRepository.delete(insertedEmployee);
            insertedEmployee = null;
        }
    }

    @Test
    @Transactional
    void createEmployee() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);
        var returnedEmployeeDTO = om.readValue(
            restEmployeeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            EmployeeDTO.class
        );

        // Validate the Employee in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedEmployee = employeeMapper.toEntity(returnedEmployeeDTO);
        assertEmployeeUpdatableFieldsEquals(returnedEmployee, getPersistedEmployee(returnedEmployee));

        insertedEmployee = returnedEmployee;
    }

    @Test
    @Transactional
    void createEmployeeWithExistingId() throws Exception {
        // Create the Employee with an existing ID
        employee.setId(1L);
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restEmployeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkCodeAssignedWhenMissing() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // Tạo mới không có mã: máy chủ cấp mã kế tiếp của dãy (xem EmployeeCodeIT).
        employee.setCode(null);
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        String body = restEmployeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertThat(om.readValue(body, EmployeeDTO.class).getCode()).isNotBlank();
        assertThat(getRepositoryCount()).isEqualTo(databaseSizeBeforeTest + 1);
    }

    @Test
    @Transactional
    void checkPersonTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employee.setPersonType(null);

        // Create the Employee, which fails.
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        restEmployeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkFullNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        employee.setFullName(null);

        // Create the Employee, which fails.
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        restEmployeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllEmployees() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(employee.getId().intValue())))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].personType").value(hasItem(DEFAULT_PERSON_TYPE.toString())))
            .andExpect(jsonPath("$.[*].fullName").value(hasItem(DEFAULT_FULL_NAME)))
            .andExpect(jsonPath("$.[*].searchText").value(hasItem(DEFAULT_SEARCH_TEXT)))
            .andExpect(jsonPath("$.[*].gender").value(hasItem(DEFAULT_GENDER.toString())))
            .andExpect(jsonPath("$.[*].birthDate").value(hasItem(DEFAULT_BIRTH_DATE.toString())))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].phone").value(hasItem(DEFAULT_PHONE)))
            .andExpect(jsonPath("$.[*].residence").value(hasItem(DEFAULT_RESIDENCE)))
            .andExpect(jsonPath("$.[*].education").value(hasItem(DEFAULT_EDUCATION)))
            .andExpect(jsonPath("$.[*].socialInsuranceStart").value(hasItem(DEFAULT_SOCIAL_INSURANCE_START.toString())))
            .andExpect(jsonPath("$.[*].socialInsuranceSalary").value(hasItem(DEFAULT_SOCIAL_INSURANCE_SALARY.intValue())))
            .andExpect(jsonPath("$.[*].dependants").value(hasItem(DEFAULT_DEPENDANTS)))
            .andExpect(jsonPath("$.[*].position").value(hasItem(DEFAULT_POSITION)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].joinDate").value(hasItem(DEFAULT_JOIN_DATE.toString())))
            .andExpect(jsonPath("$.[*].lastWorkingDay").value(hasItem(DEFAULT_LAST_WORKING_DAY.toString())))
            .andExpect(jsonPath("$.[*].terminationType").value(hasItem(DEFAULT_TERMINATION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].terminationReason").value(hasItem(DEFAULT_TERMINATION_REASON)))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeesWithEagerRelationshipsIsEnabled() throws Exception {
        when(employeeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restEmployeeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(employeeServiceMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllEmployeesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(employeeServiceMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restEmployeeMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(employeeRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @Transactional
    void getEmployee() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get the employee
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL_ID, employee.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(employee.getId().intValue()))
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE))
            .andExpect(jsonPath("$.personType").value(DEFAULT_PERSON_TYPE.toString()))
            .andExpect(jsonPath("$.fullName").value(DEFAULT_FULL_NAME))
            .andExpect(jsonPath("$.searchText").value(DEFAULT_SEARCH_TEXT))
            .andExpect(jsonPath("$.gender").value(DEFAULT_GENDER.toString()))
            .andExpect(jsonPath("$.birthDate").value(DEFAULT_BIRTH_DATE.toString()))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.phone").value(DEFAULT_PHONE))
            .andExpect(jsonPath("$.residence").value(DEFAULT_RESIDENCE))
            .andExpect(jsonPath("$.education").value(DEFAULT_EDUCATION))
            .andExpect(jsonPath("$.socialInsuranceStart").value(DEFAULT_SOCIAL_INSURANCE_START.toString()))
            .andExpect(jsonPath("$.socialInsuranceSalary").value(DEFAULT_SOCIAL_INSURANCE_SALARY.intValue()))
            .andExpect(jsonPath("$.dependants").value(DEFAULT_DEPENDANTS))
            .andExpect(jsonPath("$.position").value(DEFAULT_POSITION))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.joinDate").value(DEFAULT_JOIN_DATE.toString()))
            .andExpect(jsonPath("$.lastWorkingDay").value(DEFAULT_LAST_WORKING_DAY.toString()))
            .andExpect(jsonPath("$.terminationType").value(DEFAULT_TERMINATION_TYPE.toString()))
            .andExpect(jsonPath("$.terminationReason").value(DEFAULT_TERMINATION_REASON))
            .andExpect(jsonPath("$.note").value(DEFAULT_NOTE));
    }

    @Test
    @Transactional
    void getEmployeesByIdFiltering() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        Long id = employee.getId();

        defaultEmployeeFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultEmployeeFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultEmployeeFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    @Transactional
    void getAllEmployeesByCodeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where code equals to
        defaultEmployeeFiltering("code.equals=" + DEFAULT_CODE, "code.equals=" + UPDATED_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByCodeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where code in
        defaultEmployeeFiltering("code.in=" + DEFAULT_CODE + "," + UPDATED_CODE, "code.in=" + UPDATED_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByCodeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where code is not null
        defaultEmployeeFiltering("code.specified=true", "code.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByCodeContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where code contains
        defaultEmployeeFiltering("code.contains=" + DEFAULT_CODE, "code.contains=" + UPDATED_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByCodeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where code does not contain
        defaultEmployeeFiltering("code.doesNotContain=" + UPDATED_CODE, "code.doesNotContain=" + DEFAULT_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByPersonTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where personType equals to
        defaultEmployeeFiltering("personType.equals=" + DEFAULT_PERSON_TYPE, "personType.equals=" + UPDATED_PERSON_TYPE);
    }

    @Test
    @Transactional
    void getAllEmployeesByPersonTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where personType in
        defaultEmployeeFiltering(
            "personType.in=" + DEFAULT_PERSON_TYPE + "," + UPDATED_PERSON_TYPE,
            "personType.in=" + UPDATED_PERSON_TYPE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByPersonTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where personType is not null
        defaultEmployeeFiltering("personType.specified=true", "personType.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByFullNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where fullName equals to
        defaultEmployeeFiltering("fullName.equals=" + DEFAULT_FULL_NAME, "fullName.equals=" + UPDATED_FULL_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByFullNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where fullName in
        defaultEmployeeFiltering("fullName.in=" + DEFAULT_FULL_NAME + "," + UPDATED_FULL_NAME, "fullName.in=" + UPDATED_FULL_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByFullNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where fullName is not null
        defaultEmployeeFiltering("fullName.specified=true", "fullName.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByFullNameContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where fullName contains
        defaultEmployeeFiltering("fullName.contains=" + DEFAULT_FULL_NAME, "fullName.contains=" + UPDATED_FULL_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByFullNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where fullName does not contain
        defaultEmployeeFiltering("fullName.doesNotContain=" + UPDATED_FULL_NAME, "fullName.doesNotContain=" + DEFAULT_FULL_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesBySearchTextIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where searchText equals to
        defaultEmployeeFiltering("searchText.equals=" + DEFAULT_SEARCH_TEXT, "searchText.equals=" + UPDATED_SEARCH_TEXT);
    }

    @Test
    @Transactional
    void getAllEmployeesBySearchTextIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where searchText in
        defaultEmployeeFiltering(
            "searchText.in=" + DEFAULT_SEARCH_TEXT + "," + UPDATED_SEARCH_TEXT,
            "searchText.in=" + UPDATED_SEARCH_TEXT
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySearchTextIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where searchText is not null
        defaultEmployeeFiltering("searchText.specified=true", "searchText.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesBySearchTextContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where searchText contains
        defaultEmployeeFiltering("searchText.contains=" + DEFAULT_SEARCH_TEXT, "searchText.contains=" + UPDATED_SEARCH_TEXT);
    }

    @Test
    @Transactional
    void getAllEmployeesBySearchTextNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where searchText does not contain
        defaultEmployeeFiltering("searchText.doesNotContain=" + UPDATED_SEARCH_TEXT, "searchText.doesNotContain=" + DEFAULT_SEARCH_TEXT);
    }

    @Test
    @Transactional
    void getAllEmployeesByGenderIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where gender equals to
        defaultEmployeeFiltering("gender.equals=" + DEFAULT_GENDER, "gender.equals=" + UPDATED_GENDER);
    }

    @Test
    @Transactional
    void getAllEmployeesByGenderIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where gender in
        defaultEmployeeFiltering("gender.in=" + DEFAULT_GENDER + "," + UPDATED_GENDER, "gender.in=" + UPDATED_GENDER);
    }

    @Test
    @Transactional
    void getAllEmployeesByGenderIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where gender is not null
        defaultEmployeeFiltering("gender.specified=true", "gender.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate equals to
        defaultEmployeeFiltering("birthDate.equals=" + DEFAULT_BIRTH_DATE, "birthDate.equals=" + UPDATED_BIRTH_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate in
        defaultEmployeeFiltering("birthDate.in=" + DEFAULT_BIRTH_DATE + "," + UPDATED_BIRTH_DATE, "birthDate.in=" + UPDATED_BIRTH_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate is not null
        defaultEmployeeFiltering("birthDate.specified=true", "birthDate.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate is greater than or equal to
        defaultEmployeeFiltering(
            "birthDate.greaterThanOrEqual=" + DEFAULT_BIRTH_DATE,
            "birthDate.greaterThanOrEqual=" + UPDATED_BIRTH_DATE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate is less than or equal to
        defaultEmployeeFiltering("birthDate.lessThanOrEqual=" + DEFAULT_BIRTH_DATE, "birthDate.lessThanOrEqual=" + SMALLER_BIRTH_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate is less than
        defaultEmployeeFiltering("birthDate.lessThan=" + UPDATED_BIRTH_DATE, "birthDate.lessThan=" + DEFAULT_BIRTH_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByBirthDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where birthDate is greater than
        defaultEmployeeFiltering("birthDate.greaterThan=" + SMALLER_BIRTH_DATE, "birthDate.greaterThan=" + DEFAULT_BIRTH_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByEmailIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where email equals to
        defaultEmployeeFiltering("email.equals=" + DEFAULT_EMAIL, "email.equals=" + UPDATED_EMAIL);
    }

    @Test
    @Transactional
    void getAllEmployeesByEmailIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where email in
        defaultEmployeeFiltering("email.in=" + DEFAULT_EMAIL + "," + UPDATED_EMAIL, "email.in=" + UPDATED_EMAIL);
    }

    @Test
    @Transactional
    void getAllEmployeesByEmailIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where email is not null
        defaultEmployeeFiltering("email.specified=true", "email.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByEmailContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where email contains
        defaultEmployeeFiltering("email.contains=" + DEFAULT_EMAIL, "email.contains=" + UPDATED_EMAIL);
    }

    @Test
    @Transactional
    void getAllEmployeesByEmailNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where email does not contain
        defaultEmployeeFiltering("email.doesNotContain=" + UPDATED_EMAIL, "email.doesNotContain=" + DEFAULT_EMAIL);
    }

    @Test
    @Transactional
    void getAllEmployeesByPhoneIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where phone equals to
        defaultEmployeeFiltering("phone.equals=" + DEFAULT_PHONE, "phone.equals=" + UPDATED_PHONE);
    }

    @Test
    @Transactional
    void getAllEmployeesByPhoneIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where phone in
        defaultEmployeeFiltering("phone.in=" + DEFAULT_PHONE + "," + UPDATED_PHONE, "phone.in=" + UPDATED_PHONE);
    }

    @Test
    @Transactional
    void getAllEmployeesByPhoneIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where phone is not null
        defaultEmployeeFiltering("phone.specified=true", "phone.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByPhoneContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where phone contains
        defaultEmployeeFiltering("phone.contains=" + DEFAULT_PHONE, "phone.contains=" + UPDATED_PHONE);
    }

    @Test
    @Transactional
    void getAllEmployeesByPhoneNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where phone does not contain
        defaultEmployeeFiltering("phone.doesNotContain=" + UPDATED_PHONE, "phone.doesNotContain=" + DEFAULT_PHONE);
    }

    @Test
    @Transactional
    void getAllEmployeesByResidenceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where residence equals to
        defaultEmployeeFiltering("residence.equals=" + DEFAULT_RESIDENCE, "residence.equals=" + UPDATED_RESIDENCE);
    }

    @Test
    @Transactional
    void getAllEmployeesByResidenceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where residence in
        defaultEmployeeFiltering("residence.in=" + DEFAULT_RESIDENCE + "," + UPDATED_RESIDENCE, "residence.in=" + UPDATED_RESIDENCE);
    }

    @Test
    @Transactional
    void getAllEmployeesByResidenceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where residence is not null
        defaultEmployeeFiltering("residence.specified=true", "residence.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByResidenceContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where residence contains
        defaultEmployeeFiltering("residence.contains=" + DEFAULT_RESIDENCE, "residence.contains=" + UPDATED_RESIDENCE);
    }

    @Test
    @Transactional
    void getAllEmployeesByResidenceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where residence does not contain
        defaultEmployeeFiltering("residence.doesNotContain=" + UPDATED_RESIDENCE, "residence.doesNotContain=" + DEFAULT_RESIDENCE);
    }

    @Test
    @Transactional
    void getAllEmployeesByEducationIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where education equals to
        defaultEmployeeFiltering("education.equals=" + DEFAULT_EDUCATION, "education.equals=" + UPDATED_EDUCATION);
    }

    @Test
    @Transactional
    void getAllEmployeesByEducationIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where education in
        defaultEmployeeFiltering("education.in=" + DEFAULT_EDUCATION + "," + UPDATED_EDUCATION, "education.in=" + UPDATED_EDUCATION);
    }

    @Test
    @Transactional
    void getAllEmployeesByEducationIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where education is not null
        defaultEmployeeFiltering("education.specified=true", "education.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByEducationContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where education contains
        defaultEmployeeFiltering("education.contains=" + DEFAULT_EDUCATION, "education.contains=" + UPDATED_EDUCATION);
    }

    @Test
    @Transactional
    void getAllEmployeesByEducationNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where education does not contain
        defaultEmployeeFiltering("education.doesNotContain=" + UPDATED_EDUCATION, "education.doesNotContain=" + DEFAULT_EDUCATION);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdNumberIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idNumber equals to
        defaultEmployeeFiltering("idNumber.equals=" + DEFAULT_ID_NUMBER, "idNumber.equals=" + UPDATED_ID_NUMBER);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdNumberIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idNumber in
        defaultEmployeeFiltering("idNumber.in=" + DEFAULT_ID_NUMBER + "," + UPDATED_ID_NUMBER, "idNumber.in=" + UPDATED_ID_NUMBER);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdNumberIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idNumber is not null
        defaultEmployeeFiltering("idNumber.specified=true", "idNumber.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByIdNumberContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idNumber contains
        defaultEmployeeFiltering("idNumber.contains=" + DEFAULT_ID_NUMBER, "idNumber.contains=" + UPDATED_ID_NUMBER);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdNumberNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idNumber does not contain
        defaultEmployeeFiltering("idNumber.doesNotContain=" + UPDATED_ID_NUMBER, "idNumber.doesNotContain=" + DEFAULT_ID_NUMBER);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate equals to
        defaultEmployeeFiltering("idIssueDate.equals=" + DEFAULT_ID_ISSUE_DATE, "idIssueDate.equals=" + UPDATED_ID_ISSUE_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate in
        defaultEmployeeFiltering(
            "idIssueDate.in=" + DEFAULT_ID_ISSUE_DATE + "," + UPDATED_ID_ISSUE_DATE,
            "idIssueDate.in=" + UPDATED_ID_ISSUE_DATE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate is not null
        defaultEmployeeFiltering("idIssueDate.specified=true", "idIssueDate.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate is greater than or equal to
        defaultEmployeeFiltering(
            "idIssueDate.greaterThanOrEqual=" + DEFAULT_ID_ISSUE_DATE,
            "idIssueDate.greaterThanOrEqual=" + UPDATED_ID_ISSUE_DATE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate is less than or equal to
        defaultEmployeeFiltering(
            "idIssueDate.lessThanOrEqual=" + DEFAULT_ID_ISSUE_DATE,
            "idIssueDate.lessThanOrEqual=" + SMALLER_ID_ISSUE_DATE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate is less than
        defaultEmployeeFiltering("idIssueDate.lessThan=" + UPDATED_ID_ISSUE_DATE, "idIssueDate.lessThan=" + DEFAULT_ID_ISSUE_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssueDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssueDate is greater than
        defaultEmployeeFiltering("idIssueDate.greaterThan=" + SMALLER_ID_ISSUE_DATE, "idIssueDate.greaterThan=" + DEFAULT_ID_ISSUE_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssuePlaceIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssuePlace equals to
        defaultEmployeeFiltering("idIssuePlace.equals=" + DEFAULT_ID_ISSUE_PLACE, "idIssuePlace.equals=" + UPDATED_ID_ISSUE_PLACE);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssuePlaceIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssuePlace in
        defaultEmployeeFiltering(
            "idIssuePlace.in=" + DEFAULT_ID_ISSUE_PLACE + "," + UPDATED_ID_ISSUE_PLACE,
            "idIssuePlace.in=" + UPDATED_ID_ISSUE_PLACE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssuePlaceIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssuePlace is not null
        defaultEmployeeFiltering("idIssuePlace.specified=true", "idIssuePlace.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssuePlaceContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssuePlace contains
        defaultEmployeeFiltering("idIssuePlace.contains=" + DEFAULT_ID_ISSUE_PLACE, "idIssuePlace.contains=" + UPDATED_ID_ISSUE_PLACE);
    }

    @Test
    @Transactional
    void getAllEmployeesByIdIssuePlaceNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where idIssuePlace does not contain
        defaultEmployeeFiltering(
            "idIssuePlace.doesNotContain=" + UPDATED_ID_ISSUE_PLACE,
            "idIssuePlace.doesNotContain=" + DEFAULT_ID_ISSUE_PLACE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTaxCodeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where taxCode equals to
        defaultEmployeeFiltering("taxCode.equals=" + DEFAULT_TAX_CODE, "taxCode.equals=" + UPDATED_TAX_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByTaxCodeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where taxCode in
        defaultEmployeeFiltering("taxCode.in=" + DEFAULT_TAX_CODE + "," + UPDATED_TAX_CODE, "taxCode.in=" + UPDATED_TAX_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByTaxCodeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where taxCode is not null
        defaultEmployeeFiltering("taxCode.specified=true", "taxCode.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByTaxCodeContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where taxCode contains
        defaultEmployeeFiltering("taxCode.contains=" + DEFAULT_TAX_CODE, "taxCode.contains=" + UPDATED_TAX_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByTaxCodeNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where taxCode does not contain
        defaultEmployeeFiltering("taxCode.doesNotContain=" + UPDATED_TAX_CODE, "taxCode.doesNotContain=" + DEFAULT_TAX_CODE);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankAccountIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankAccount equals to
        defaultEmployeeFiltering("bankAccount.equals=" + DEFAULT_BANK_ACCOUNT, "bankAccount.equals=" + UPDATED_BANK_ACCOUNT);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankAccountIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankAccount in
        defaultEmployeeFiltering(
            "bankAccount.in=" + DEFAULT_BANK_ACCOUNT + "," + UPDATED_BANK_ACCOUNT,
            "bankAccount.in=" + UPDATED_BANK_ACCOUNT
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByBankAccountIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankAccount is not null
        defaultEmployeeFiltering("bankAccount.specified=true", "bankAccount.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByBankAccountContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankAccount contains
        defaultEmployeeFiltering("bankAccount.contains=" + DEFAULT_BANK_ACCOUNT, "bankAccount.contains=" + UPDATED_BANK_ACCOUNT);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankAccountNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankAccount does not contain
        defaultEmployeeFiltering(
            "bankAccount.doesNotContain=" + UPDATED_BANK_ACCOUNT,
            "bankAccount.doesNotContain=" + DEFAULT_BANK_ACCOUNT
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByBankNameIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankName equals to
        defaultEmployeeFiltering("bankName.equals=" + DEFAULT_BANK_NAME, "bankName.equals=" + UPDATED_BANK_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankNameIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankName in
        defaultEmployeeFiltering("bankName.in=" + DEFAULT_BANK_NAME + "," + UPDATED_BANK_NAME, "bankName.in=" + UPDATED_BANK_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankNameIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankName is not null
        defaultEmployeeFiltering("bankName.specified=true", "bankName.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByBankNameContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankName contains
        defaultEmployeeFiltering("bankName.contains=" + DEFAULT_BANK_NAME, "bankName.contains=" + UPDATED_BANK_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesByBankNameNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where bankName does not contain
        defaultEmployeeFiltering("bankName.doesNotContain=" + UPDATED_BANK_NAME, "bankName.doesNotContain=" + DEFAULT_BANK_NAME);
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceNoIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceNo equals to
        defaultEmployeeFiltering(
            "socialInsuranceNo.equals=" + DEFAULT_SOCIAL_INSURANCE_NO,
            "socialInsuranceNo.equals=" + UPDATED_SOCIAL_INSURANCE_NO
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceNoIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceNo in
        defaultEmployeeFiltering(
            "socialInsuranceNo.in=" + DEFAULT_SOCIAL_INSURANCE_NO + "," + UPDATED_SOCIAL_INSURANCE_NO,
            "socialInsuranceNo.in=" + UPDATED_SOCIAL_INSURANCE_NO
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceNoIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceNo is not null
        defaultEmployeeFiltering("socialInsuranceNo.specified=true", "socialInsuranceNo.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceNoContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceNo contains
        defaultEmployeeFiltering(
            "socialInsuranceNo.contains=" + DEFAULT_SOCIAL_INSURANCE_NO,
            "socialInsuranceNo.contains=" + UPDATED_SOCIAL_INSURANCE_NO
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceNoNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceNo does not contain
        defaultEmployeeFiltering(
            "socialInsuranceNo.doesNotContain=" + UPDATED_SOCIAL_INSURANCE_NO,
            "socialInsuranceNo.doesNotContain=" + DEFAULT_SOCIAL_INSURANCE_NO
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart equals to
        defaultEmployeeFiltering(
            "socialInsuranceStart.equals=" + DEFAULT_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.equals=" + UPDATED_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart in
        defaultEmployeeFiltering(
            "socialInsuranceStart.in=" + DEFAULT_SOCIAL_INSURANCE_START + "," + UPDATED_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.in=" + UPDATED_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart is not null
        defaultEmployeeFiltering("socialInsuranceStart.specified=true", "socialInsuranceStart.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart is greater than or equal to
        defaultEmployeeFiltering(
            "socialInsuranceStart.greaterThanOrEqual=" + DEFAULT_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.greaterThanOrEqual=" + UPDATED_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart is less than or equal to
        defaultEmployeeFiltering(
            "socialInsuranceStart.lessThanOrEqual=" + DEFAULT_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.lessThanOrEqual=" + SMALLER_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart is less than
        defaultEmployeeFiltering(
            "socialInsuranceStart.lessThan=" + UPDATED_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.lessThan=" + DEFAULT_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceStartIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceStart is greater than
        defaultEmployeeFiltering(
            "socialInsuranceStart.greaterThan=" + SMALLER_SOCIAL_INSURANCE_START,
            "socialInsuranceStart.greaterThan=" + DEFAULT_SOCIAL_INSURANCE_START
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary equals to
        defaultEmployeeFiltering(
            "socialInsuranceSalary.equals=" + DEFAULT_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.equals=" + UPDATED_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary in
        defaultEmployeeFiltering(
            "socialInsuranceSalary.in=" + DEFAULT_SOCIAL_INSURANCE_SALARY + "," + UPDATED_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.in=" + UPDATED_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary is not null
        defaultEmployeeFiltering("socialInsuranceSalary.specified=true", "socialInsuranceSalary.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary is greater than or equal to
        defaultEmployeeFiltering(
            "socialInsuranceSalary.greaterThanOrEqual=" + DEFAULT_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.greaterThanOrEqual=" + UPDATED_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary is less than or equal to
        defaultEmployeeFiltering(
            "socialInsuranceSalary.lessThanOrEqual=" + DEFAULT_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.lessThanOrEqual=" + SMALLER_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary is less than
        defaultEmployeeFiltering(
            "socialInsuranceSalary.lessThan=" + UPDATED_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.lessThan=" + DEFAULT_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesBySocialInsuranceSalaryIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where socialInsuranceSalary is greater than
        defaultEmployeeFiltering(
            "socialInsuranceSalary.greaterThan=" + SMALLER_SOCIAL_INSURANCE_SALARY,
            "socialInsuranceSalary.greaterThan=" + DEFAULT_SOCIAL_INSURANCE_SALARY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants equals to
        defaultEmployeeFiltering("dependants.equals=" + DEFAULT_DEPENDANTS, "dependants.equals=" + UPDATED_DEPENDANTS);
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants in
        defaultEmployeeFiltering("dependants.in=" + DEFAULT_DEPENDANTS + "," + UPDATED_DEPENDANTS, "dependants.in=" + UPDATED_DEPENDANTS);
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants is not null
        defaultEmployeeFiltering("dependants.specified=true", "dependants.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants is greater than or equal to
        defaultEmployeeFiltering(
            "dependants.greaterThanOrEqual=" + DEFAULT_DEPENDANTS,
            "dependants.greaterThanOrEqual=" + UPDATED_DEPENDANTS
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants is less than or equal to
        defaultEmployeeFiltering("dependants.lessThanOrEqual=" + DEFAULT_DEPENDANTS, "dependants.lessThanOrEqual=" + SMALLER_DEPENDANTS);
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants is less than
        defaultEmployeeFiltering("dependants.lessThan=" + UPDATED_DEPENDANTS, "dependants.lessThan=" + DEFAULT_DEPENDANTS);
    }

    @Test
    @Transactional
    void getAllEmployeesByDependantsIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where dependants is greater than
        defaultEmployeeFiltering("dependants.greaterThan=" + SMALLER_DEPENDANTS, "dependants.greaterThan=" + DEFAULT_DEPENDANTS);
    }

    @Test
    @Transactional
    void getAllEmployeesByPositionIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where position equals to
        defaultEmployeeFiltering("position.equals=" + DEFAULT_POSITION, "position.equals=" + UPDATED_POSITION);
    }

    @Test
    @Transactional
    void getAllEmployeesByPositionIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where position in
        defaultEmployeeFiltering("position.in=" + DEFAULT_POSITION + "," + UPDATED_POSITION, "position.in=" + UPDATED_POSITION);
    }

    @Test
    @Transactional
    void getAllEmployeesByPositionIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where position is not null
        defaultEmployeeFiltering("position.specified=true", "position.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByPositionContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where position contains
        defaultEmployeeFiltering("position.contains=" + DEFAULT_POSITION, "position.contains=" + UPDATED_POSITION);
    }

    @Test
    @Transactional
    void getAllEmployeesByPositionNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where position does not contain
        defaultEmployeeFiltering("position.doesNotContain=" + UPDATED_POSITION, "position.doesNotContain=" + DEFAULT_POSITION);
    }

    @Test
    @Transactional
    void getAllEmployeesByStatusIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where status equals to
        defaultEmployeeFiltering("status.equals=" + DEFAULT_STATUS, "status.equals=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllEmployeesByStatusIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where status in
        defaultEmployeeFiltering("status.in=" + DEFAULT_STATUS + "," + UPDATED_STATUS, "status.in=" + UPDATED_STATUS);
    }

    @Test
    @Transactional
    void getAllEmployeesByStatusIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where status is not null
        defaultEmployeeFiltering("status.specified=true", "status.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate equals to
        defaultEmployeeFiltering("joinDate.equals=" + DEFAULT_JOIN_DATE, "joinDate.equals=" + UPDATED_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate in
        defaultEmployeeFiltering("joinDate.in=" + DEFAULT_JOIN_DATE + "," + UPDATED_JOIN_DATE, "joinDate.in=" + UPDATED_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate is not null
        defaultEmployeeFiltering("joinDate.specified=true", "joinDate.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate is greater than or equal to
        defaultEmployeeFiltering("joinDate.greaterThanOrEqual=" + DEFAULT_JOIN_DATE, "joinDate.greaterThanOrEqual=" + UPDATED_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate is less than or equal to
        defaultEmployeeFiltering("joinDate.lessThanOrEqual=" + DEFAULT_JOIN_DATE, "joinDate.lessThanOrEqual=" + SMALLER_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate is less than
        defaultEmployeeFiltering("joinDate.lessThan=" + UPDATED_JOIN_DATE, "joinDate.lessThan=" + DEFAULT_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByJoinDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where joinDate is greater than
        defaultEmployeeFiltering("joinDate.greaterThan=" + SMALLER_JOIN_DATE, "joinDate.greaterThan=" + DEFAULT_JOIN_DATE);
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay equals to
        defaultEmployeeFiltering("lastWorkingDay.equals=" + DEFAULT_LAST_WORKING_DAY, "lastWorkingDay.equals=" + UPDATED_LAST_WORKING_DAY);
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay in
        defaultEmployeeFiltering(
            "lastWorkingDay.in=" + DEFAULT_LAST_WORKING_DAY + "," + UPDATED_LAST_WORKING_DAY,
            "lastWorkingDay.in=" + UPDATED_LAST_WORKING_DAY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay is not null
        defaultEmployeeFiltering("lastWorkingDay.specified=true", "lastWorkingDay.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay is greater than or equal to
        defaultEmployeeFiltering(
            "lastWorkingDay.greaterThanOrEqual=" + DEFAULT_LAST_WORKING_DAY,
            "lastWorkingDay.greaterThanOrEqual=" + UPDATED_LAST_WORKING_DAY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay is less than or equal to
        defaultEmployeeFiltering(
            "lastWorkingDay.lessThanOrEqual=" + DEFAULT_LAST_WORKING_DAY,
            "lastWorkingDay.lessThanOrEqual=" + SMALLER_LAST_WORKING_DAY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay is less than
        defaultEmployeeFiltering(
            "lastWorkingDay.lessThan=" + UPDATED_LAST_WORKING_DAY,
            "lastWorkingDay.lessThan=" + DEFAULT_LAST_WORKING_DAY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByLastWorkingDayIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where lastWorkingDay is greater than
        defaultEmployeeFiltering(
            "lastWorkingDay.greaterThan=" + SMALLER_LAST_WORKING_DAY,
            "lastWorkingDay.greaterThan=" + DEFAULT_LAST_WORKING_DAY
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationTypeIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationType equals to
        defaultEmployeeFiltering(
            "terminationType.equals=" + DEFAULT_TERMINATION_TYPE,
            "terminationType.equals=" + UPDATED_TERMINATION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationTypeIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationType in
        defaultEmployeeFiltering(
            "terminationType.in=" + DEFAULT_TERMINATION_TYPE + "," + UPDATED_TERMINATION_TYPE,
            "terminationType.in=" + UPDATED_TERMINATION_TYPE
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationTypeIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationType is not null
        defaultEmployeeFiltering("terminationType.specified=true", "terminationType.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationReasonIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationReason equals to
        defaultEmployeeFiltering(
            "terminationReason.equals=" + DEFAULT_TERMINATION_REASON,
            "terminationReason.equals=" + UPDATED_TERMINATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationReasonIsInShouldWork() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationReason in
        defaultEmployeeFiltering(
            "terminationReason.in=" + DEFAULT_TERMINATION_REASON + "," + UPDATED_TERMINATION_REASON,
            "terminationReason.in=" + UPDATED_TERMINATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationReasonIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationReason is not null
        defaultEmployeeFiltering("terminationReason.specified=true", "terminationReason.specified=false");
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationReasonContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationReason contains
        defaultEmployeeFiltering(
            "terminationReason.contains=" + DEFAULT_TERMINATION_REASON,
            "terminationReason.contains=" + UPDATED_TERMINATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByTerminationReasonNotContainsSomething() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        // Get all the employeeList where terminationReason does not contain
        defaultEmployeeFiltering(
            "terminationReason.doesNotContain=" + UPDATED_TERMINATION_REASON,
            "terminationReason.doesNotContain=" + DEFAULT_TERMINATION_REASON
        );
    }

    @Test
    @Transactional
    void getAllEmployeesByDepartmentIsEqualToSomething() throws Exception {
        Department department;
        if (TestUtil.findAll(em, Department.class).isEmpty()) {
            employeeRepository.saveAndFlush(employee);
            department = DepartmentResourceIT.createEntity();
        } else {
            department = TestUtil.findAll(em, Department.class).getFirst();
        }
        em.persist(department);
        em.flush();
        employee.setDepartment(department);
        employeeRepository.saveAndFlush(employee);
        Long departmentId = department.getId();
        // Get all the employeeList where department equals to departmentId
        defaultEmployeeShouldBeFound("departmentId.equals=" + departmentId);

        // Get all the employeeList where department equals to (departmentId + 1)
        defaultEmployeeShouldNotBeFound("departmentId.equals=" + (departmentId + 1));
    }

    @Test
    @Transactional
    void getAllEmployeesByManagerIsEqualToSomething() throws Exception {
        Employee manager;
        if (TestUtil.findAll(em, Employee.class).isEmpty()) {
            employeeRepository.saveAndFlush(employee);
            manager = EmployeeResourceIT.createEntity();
            manager.setCode("MANAGER01"); // code is unique; avoid clashing with the default test entity
        } else {
            manager = TestUtil.findAll(em, Employee.class).getFirst();
        }
        em.persist(manager);
        em.flush();
        employee.setManager(manager);
        employeeRepository.saveAndFlush(employee);
        Long managerId = manager.getId();
        // Get all the employeeList where manager equals to managerId
        defaultEmployeeShouldBeFound("managerId.equals=" + managerId);

        // Get all the employeeList where manager equals to (managerId + 1)
        defaultEmployeeShouldNotBeFound("managerId.equals=" + (managerId + 1));
    }

    @Test
    @Transactional
    void getAllEmployeesByUserIsEqualToSomething() throws Exception {
        User user;
        if (TestUtil.findAll(em, User.class).isEmpty()) {
            employeeRepository.saveAndFlush(employee);
            user = UserResourceIT.createEntity();
        } else {
            user = TestUtil.findAll(em, User.class).getFirst();
        }
        em.persist(user);
        em.flush();
        employee.setUser(user);
        employeeRepository.saveAndFlush(employee);
        Long userId = user.getId();
        // Get all the employeeList where user equals to userId
        defaultEmployeeShouldBeFound("userId.equals=" + userId);

        // Get all the employeeList where user equals to (userId + 1)
        defaultEmployeeShouldNotBeFound("userId.equals=" + (userId + 1));
    }

    private void defaultEmployeeFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultEmployeeShouldBeFound(shouldBeFound);
        defaultEmployeeShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultEmployeeShouldBeFound(String filter) throws Exception {
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(employee.getId().intValue())))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].personType").value(hasItem(DEFAULT_PERSON_TYPE.toString())))
            .andExpect(jsonPath("$.[*].fullName").value(hasItem(DEFAULT_FULL_NAME)))
            .andExpect(jsonPath("$.[*].searchText").value(hasItem(DEFAULT_SEARCH_TEXT)))
            .andExpect(jsonPath("$.[*].gender").value(hasItem(DEFAULT_GENDER.toString())))
            .andExpect(jsonPath("$.[*].birthDate").value(hasItem(DEFAULT_BIRTH_DATE.toString())))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].phone").value(hasItem(DEFAULT_PHONE)))
            .andExpect(jsonPath("$.[*].residence").value(hasItem(DEFAULT_RESIDENCE)))
            .andExpect(jsonPath("$.[*].education").value(hasItem(DEFAULT_EDUCATION)))
            .andExpect(jsonPath("$.[*].socialInsuranceStart").value(hasItem(DEFAULT_SOCIAL_INSURANCE_START.toString())))
            .andExpect(jsonPath("$.[*].socialInsuranceSalary").value(hasItem(DEFAULT_SOCIAL_INSURANCE_SALARY.intValue())))
            .andExpect(jsonPath("$.[*].dependants").value(hasItem(DEFAULT_DEPENDANTS)))
            .andExpect(jsonPath("$.[*].position").value(hasItem(DEFAULT_POSITION)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].joinDate").value(hasItem(DEFAULT_JOIN_DATE.toString())))
            .andExpect(jsonPath("$.[*].lastWorkingDay").value(hasItem(DEFAULT_LAST_WORKING_DAY.toString())))
            .andExpect(jsonPath("$.[*].terminationType").value(hasItem(DEFAULT_TERMINATION_TYPE.toString())))
            .andExpect(jsonPath("$.[*].terminationReason").value(hasItem(DEFAULT_TERMINATION_REASON)))
            .andExpect(jsonPath("$.[*].note").value(hasItem(DEFAULT_NOTE)));

        // Check, that the count call also returns 1
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("1"));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultEmployeeShouldNotBeFound(String filter) throws Exception {
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(content().string("0"));
    }

    @Test
    @Transactional
    void getNonExistingEmployee() throws Exception {
        // Get the employee
        restEmployeeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingEmployee() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employee
        Employee updatedEmployee = employeeRepository.findById(employee.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedEmployee are not directly saved in db
        em.detach(updatedEmployee);
        updatedEmployee
            .code(UPDATED_CODE)
            .personType(DEFAULT_PERSON_TYPE)
            .fullName(UPDATED_FULL_NAME)
            .searchText(UPDATED_SEARCH_TEXT)
            .gender(UPDATED_GENDER)
            .birthDate(UPDATED_BIRTH_DATE)
            .email(UPDATED_EMAIL)
            .phone(UPDATED_PHONE)
            .residence(UPDATED_RESIDENCE)
            .education(UPDATED_EDUCATION)
            .idNumber(UPDATED_ID_NUMBER)
            .idIssueDate(UPDATED_ID_ISSUE_DATE)
            .idIssuePlace(UPDATED_ID_ISSUE_PLACE)
            .taxCode(UPDATED_TAX_CODE)
            .bankAccount(UPDATED_BANK_ACCOUNT)
            .bankName(UPDATED_BANK_NAME)
            .socialInsuranceNo(UPDATED_SOCIAL_INSURANCE_NO)
            .socialInsuranceStart(UPDATED_SOCIAL_INSURANCE_START)
            .socialInsuranceSalary(UPDATED_SOCIAL_INSURANCE_SALARY)
            .dependants(UPDATED_DEPENDANTS)
            .position(UPDATED_POSITION)
            .status(DEFAULT_STATUS) // trạng thái chỉ đổi qua /terminate
            .joinDate(UPDATED_JOIN_DATE)
            .lastWorkingDay(DEFAULT_LAST_WORKING_DAY) // chỉ đổi qua /terminate
            .terminationType(DEFAULT_TERMINATION_TYPE)
            .terminationReason(DEFAULT_TERMINATION_REASON)
            .note(UPDATED_NOTE);
        EmployeeDTO employeeDTO = employeeMapper.toDto(updatedEmployee);
        // Trường nhạy cảm là write-only trên DTO: thêm vào JSON gửi lên như client thật.
        tools.jackson.databind.node.ObjectNode body = om.valueToTree(employeeDTO);
        body.put("idNumber", UPDATED_ID_NUMBER);
        body.put("idIssueDate", UPDATED_ID_ISSUE_DATE.toString());
        body.put("idIssuePlace", UPDATED_ID_ISSUE_PLACE);
        body.put("taxCode", UPDATED_TAX_CODE);
        body.put("bankAccount", UPDATED_BANK_ACCOUNT);
        body.put("bankName", UPDATED_BANK_NAME);
        body.put("socialInsuranceNo", UPDATED_SOCIAL_INSURANCE_NO);

        restEmployeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employeeDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body))
            )
            .andExpect(status().isOk());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedEmployeeToMatchAllProperties(updatedEmployee);
    }

    @Test
    @Transactional
    void putNonExistingEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, employeeDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(employeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateEmployeeWithPatch() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employee using partial update
        Employee partialUpdatedEmployee = new Employee();
        partialUpdatedEmployee.setId(employee.getId());

        partialUpdatedEmployee
            .code(UPDATED_CODE)
            .personType(DEFAULT_PERSON_TYPE)
            .fullName(UPDATED_FULL_NAME)
            .gender(UPDATED_GENDER)
            .birthDate(UPDATED_BIRTH_DATE)
            .email(UPDATED_EMAIL)
            .phone(UPDATED_PHONE)
            .residence(UPDATED_RESIDENCE)
            .idNumber(UPDATED_ID_NUMBER)
            .idIssuePlace(UPDATED_ID_ISSUE_PLACE)
            .taxCode(UPDATED_TAX_CODE)
            .bankName(UPDATED_BANK_NAME)
            .socialInsuranceSalary(UPDATED_SOCIAL_INSURANCE_SALARY)
            .joinDate(UPDATED_JOIN_DATE)
            .terminationType(DEFAULT_TERMINATION_TYPE)
            .terminationReason(DEFAULT_TERMINATION_REASON)
            .searchText(UPDATED_SEARCH_TEXT);

        restEmployeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmployee.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmployee))
            )
            .andExpect(status().isOk());

        // Validate the Employee in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertEmployeeUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedEmployee, employee), getPersistedEmployee(employee));
    }

    @Test
    @Transactional
    void fullUpdateEmployeeWithPatch() throws Exception {
        // Initialize the database
        insertedEmployee = employeeRepository.saveAndFlush(employee);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the employee using partial update
        Employee partialUpdatedEmployee = new Employee();
        partialUpdatedEmployee.setId(employee.getId());

        partialUpdatedEmployee
            .code(UPDATED_CODE)
            .personType(DEFAULT_PERSON_TYPE)
            .fullName(UPDATED_FULL_NAME)
            .searchText(UPDATED_SEARCH_TEXT)
            .gender(UPDATED_GENDER)
            .birthDate(UPDATED_BIRTH_DATE)
            .email(UPDATED_EMAIL)
            .phone(UPDATED_PHONE)
            .residence(UPDATED_RESIDENCE)
            .education(UPDATED_EDUCATION)
            .idNumber(UPDATED_ID_NUMBER)
            .idIssueDate(UPDATED_ID_ISSUE_DATE)
            .idIssuePlace(UPDATED_ID_ISSUE_PLACE)
            .taxCode(UPDATED_TAX_CODE)
            .bankAccount(UPDATED_BANK_ACCOUNT)
            .bankName(UPDATED_BANK_NAME)
            .socialInsuranceNo(UPDATED_SOCIAL_INSURANCE_NO)
            .socialInsuranceStart(UPDATED_SOCIAL_INSURANCE_START)
            .socialInsuranceSalary(UPDATED_SOCIAL_INSURANCE_SALARY)
            .dependants(UPDATED_DEPENDANTS)
            .position(UPDATED_POSITION)
            .status(DEFAULT_STATUS) // trạng thái chỉ đổi qua /terminate
            .joinDate(UPDATED_JOIN_DATE)
            .lastWorkingDay(DEFAULT_LAST_WORKING_DAY)
            .terminationType(DEFAULT_TERMINATION_TYPE)
            .terminationReason(DEFAULT_TERMINATION_REASON)
            .note(UPDATED_NOTE);

        restEmployeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedEmployee.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedEmployee))
            )
            .andExpect(status().isOk());

        // Validate the Employee in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertEmployeeUpdatableFieldsEquals(partialUpdatedEmployee, getPersistedEmployee(partialUpdatedEmployee));
    }

    @Test
    @Transactional
    void patchNonExistingEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, employeeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(employeeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamEmployee() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        employee.setId(longCount.incrementAndGet());

        // Create the Employee
        EmployeeDTO employeeDTO = employeeMapper.toDto(employee);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restEmployeeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(employeeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Employee in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteNotAllowed() throws Exception {
        // DELETE /api/employees/{id} là xóa hẳn, chỉ login admin (EmployeeDeletionIT); HR (và mọi người khác) bị 403.
        insertedEmployee = employeeRepository.saveAndFlush(employee);
        long before = getRepositoryCount();

        int status = restEmployeeMockMvc
            .perform(
                delete(ENTITY_API_URL_ID, employee.getId())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"confirmCode\":\"" + employee.getCode() + "\",\"reason\":\"Thử\"}")
            )
            .andReturn()
            .getResponse()
            .getStatus();

        assertThat(status).isEqualTo(403);
        assertThat(employeeRepository.existsById(employee.getId())).isTrue();
        assertSameRepositoryCount(before);
    }

    private EmployeeDTO newDto(PersonType type, String code, String fullName) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setPersonType(type);
        dto.setCode(code);
        dto.setFullName(fullName);
        return dto;
    }

    private String postEmployee(EmployeeDTO dto, int expectedStatus) throws Exception {
        return restEmployeeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto)))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void createServicePerson_autoCodes() throws Exception {
        em.createNativeQuery("alter sequence hddv_code_seq restart with 1").executeUpdate();

        String first = postEmployee(newDto(PersonType.DICH_VU, null, "Người Dịch Vụ Một"), 201);
        String second = postEmployee(newDto(PersonType.DICH_VU, null, "Người Dịch Vụ Hai"), 201);

        assertThat(om.readValue(first, EmployeeDTO.class).getCode()).isEqualTo("HDDV1");
        assertThat(om.readValue(second, EmployeeDTO.class).getCode()).isEqualTo("HDDV2");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void createServicePerson_ignoresClientCode() throws Exception {
        String body = postEmployee(newDto(PersonType.DICH_VU, "X1", "Người Dịch Vụ"), 201);
        assertThat(om.readValue(body, EmployeeDTO.class).getCode()).startsWith("HDDV");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void updateServicePerson_keepsCodeAndType() throws Exception {
        EmployeeDTO created = om.readValue(postEmployee(newDto(PersonType.DICH_VU, null, "Người Dịch Vụ"), 201), EmployeeDTO.class);
        String code = created.getCode();
        created.setCode("NV9999");
        created.setPersonType(PersonType.NHAN_VIEN);
        created.setFullName("Tên Mới");

        restEmployeeMockMvc
            .perform(put(ENTITY_API_URL_ID, created.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(created)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.personType").value("DICH_VU"))
            .andExpect(jsonPath("$.searchText").value(org.hamcrest.Matchers.containsString("ten moi")));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void createStaff_blankCodeGetsNextCode() throws Exception {
        String first = postEmployee(newDto(PersonType.NHAN_VIEN, null, "Nhân Viên"), 201);
        String second = postEmployee(newDto(PersonType.NHAN_VIEN, "   ", "Nhân Viên"), 201);
        String a = om.readValue(first, EmployeeDTO.class).getCode();
        String b = om.readValue(second, EmployeeDTO.class).getCode();
        assertThat(a).startsWith("DOIT");
        assertThat(b).startsWith("DOIT").isNotEqualTo(a);
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void createStaff_normalizesCode() throws Exception {
        String body = postEmployee(newDto(PersonType.NHAN_VIEN, " nv0142 ", "Trần Minh Quân"), 201);
        assertThat(om.readValue(body, EmployeeDTO.class).getCode()).isEqualTo("NV0142");

        restEmployeeMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(newDto(PersonType.NHAN_VIEN, "NV0142", "Người Khác")))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.codeexists"));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void updateStaff_rejectsDuplicateCode() throws Exception {
        postEmployee(newDto(PersonType.NHAN_VIEN, "NV0001", "Người Một"), 201);
        EmployeeDTO other = om.readValue(postEmployee(newDto(PersonType.NHAN_VIEN, "NV0002", "Người Hai"), 201), EmployeeDTO.class);
        other.setCode(" nv0001");

        restEmployeeMockMvc
            .perform(put(ENTITY_API_URL_ID, other.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(other)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.codeexists"));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void searchIgnoresAccents() throws Exception {
        postEmployee(newDto(PersonType.NHAN_VIEN, "NV0142", "Trần Minh Quân"), 201);
        postEmployee(newDto(PersonType.NHAN_VIEN, "NV0143", "Lê Thị Hoa"), 201);

        for (String q : new String[] { "tran minh", "quan", "nv0142", "Trần  Minh", "QUÂN" }) {
            restEmployeeMockMvc
                .perform(get(ENTITY_API_URL).param("q.contains", q))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("NV0142"));
        }
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void newEmployeeDefaults() throws Exception {
        postEmployee(newDto(PersonType.NHAN_VIEN, "NV0500", "Mặc Định"), 201);

        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL).param("code.equals", "NV0500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("DANG_LAM_VIEC"))
            .andExpect(jsonPath("$[0].dependants").value(0));
    }

    @Autowired
    private vn.doit.hrm.repository.WorkSiteRepository workSiteRepository;

    @Autowired
    private vn.doit.hrm.repository.WorkAssignmentRepository workAssignmentRepository;

    @Autowired
    private vn.doit.hrm.repository.EmploymentContractRepository employmentContractRepository;

    private Employee persistEmployee(String code) {
        return employeeRepository.saveAndFlush(
            createEntity()
                .code(code)
                .fullName("Người " + code)
                .searchText(code.toLowerCase())
        );
    }

    private void assign(Employee e, vn.doit.hrm.domain.WorkSite site, LocalDate from, LocalDate to) {
        var wa = WorkAssignmentResourceIT.createEntity(em);
        wa.setEmployee(e);
        wa.setSite(site);
        wa.setFromDate(from);
        wa.setToDate(to);
        workAssignmentRepository.saveAndFlush(wa);
    }

    private void contract(
        Employee e,
        vn.doit.hrm.domain.enumeration.ContractType type,
        vn.doit.hrm.domain.enumeration.ContractStatus st,
        LocalDate from,
        LocalDate to
    ) {
        var c = EmploymentContractResourceIT.createEntity(em);
        c.setEmployee(e);
        c.setType(type);
        c.setStatus(st);
        c.setStartDate(from);
        c.setEndDate(to);
        employmentContractRepository.saveAndFlush(c);
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void putKeepsTerminationFields() throws Exception {
        Employee e = persistEmployee("NVT01");
        // Ngày làm việc cuối đã qua (theo đồng hồ thật) để được áp dụng ngay, không phải hẹn lịch.
        restEmployeeMockMvc
            .perform(
                post(ENTITY_API_URL_ID + "/terminate", e.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"lastWorkingDay\":\"2025-10-31\",\"terminationType\":\"TU_NGHI\",\"terminationReason\":\"Chuyển việc\"}")
            )
            .andExpect(status().isOk());
        em.flush();
        em.clear();

        // PUT như form: không gửi các trường nghỉ việc.
        restEmployeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, e.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":" + e.getId() + ",\"personType\":\"NHAN_VIEN\",\"code\":\"NVT01\",\"fullName\":\"Tên Mới\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DA_NGHI"))
            .andExpect(jsonPath("$.lastWorkingDay").value("2025-10-31"))
            .andExpect(jsonPath("$.terminationType").value("TU_NGHI"))
            .andExpect(jsonPath("$.terminationReason").value("Chuyển việc"));
        em.flush();
        em.clear();
        Employee after = employeeRepository.findById(e.getId()).orElseThrow();
        assertThat(after.getLastWorkingDay()).isEqualTo(LocalDate.of(2025, 10, 31));
        assertThat(after.getTerminationType()).isEqualTo(TerminationType.TU_NGHI);
        assertThat(after.getTerminationReason()).isEqualTo("Chuyển việc");
        assertThat(after.getFullName()).isEqualTo("Tên Mới");
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void putKeepsUserLink() throws Exception {
        User u = UserResourceIT.initTestUser();
        em.persist(u);
        Employee e = persistEmployee("NVU01");
        e.setUser(u);
        employeeRepository.saveAndFlush(e);
        em.clear();

        restEmployeeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, e.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"id\":" + e.getId() + ",\"personType\":\"NHAN_VIEN\",\"code\":\"NVU01\",\"fullName\":\"Tên Mới\"}")
            )
            .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(employeeRepository.findById(e.getId()).orElseThrow().getUser()).isNotNull();
        assertThat(employeeRepository.findById(e.getId()).orElseThrow().getUser().getId()).isEqualTo(u.getId());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void listAndDetailShowCurrentSiteName() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var lp = workSiteRepository.saveAndFlush(
            new WorkSite().name("Khách hàng IT danh sách").type(WorkLocationType.KHACH_HANG).active(true)
        );
        Employee withSite = persistEmployee("NVS01");
        Employee without = persistEmployee("NVS02");
        Employee past = persistEmployee("NVS03");
        assign(withSite, lp, today.minusDays(30), null);
        assign(past, lp, today.minusDays(60), today.minusDays(1));

        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL).param("code.in", "NVS01,NVS02,NVS03").param("sort", "code,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("NVS01"))
            .andExpect(jsonPath("$[0].currentSiteName").value("Khách hàng IT danh sách"))
            .andExpect(jsonPath("$[1].currentSiteName").doesNotExist())
            .andExpect(jsonPath("$[2].currentSiteName").doesNotExist());
        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL_ID, withSite.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentSiteName").value("Khách hàng IT danh sách"));
        restEmployeeMockMvc.perform(get(ENTITY_API_URL_ID, without.getId())).andExpect(jsonPath("$.currentSiteName").doesNotExist());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_HR")
    void filterBySiteAndContract() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        // "Văn phòng" được seed bởi Liquibase; khách hàng tạo trong test.
        var lp = workSiteRepository.saveAndFlush(
            new WorkSite().name("Khách hàng IT danh sách").type(WorkLocationType.KHACH_HANG).active(true)
        );
        var office = workSiteRepository
            .findAll()
            .stream()
            .filter(w -> "Văn phòng".equals(w.getName()))
            .findFirst()
            .orElseThrow();
        Employee a = persistEmployee("NVA01");
        Employee b = persistEmployee("NVB01");
        Employee oldAtLp = persistEmployee("NVC01");

        assign(a, lp, today.minusDays(30), null);
        assign(b, office, today.minusDays(30), null);
        assign(oldAtLp, lp, today.minusDays(60), today.minusDays(1));

        contract(
            a,
            vn.doit.hrm.domain.enumeration.ContractType.HDLD_XDTH,
            vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY,
            today.minusDays(30),
            today.plusDays(300)
        );
        contract(
            b,
            vn.doit.hrm.domain.enumeration.ContractType.HDTV,
            vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY,
            today.minusDays(10),
            today.plusDays(50)
        );
        contract(
            oldAtLp,
            vn.doit.hrm.domain.enumeration.ContractType.HDTV,
            vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY,
            today.minusDays(100),
            today.minusDays(2)
        );
        contract(
            oldAtLp,
            vn.doit.hrm.domain.enumeration.ContractType.HDTV,
            vn.doit.hrm.domain.enumeration.ContractStatus.NHAP,
            today.minusDays(1),
            null
        );

        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL).param("currentSiteId.equals", lp.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("NVA01"));

        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL).param("currentContractType.equals", "HDTV"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("NVB01"));

        restEmployeeMockMvc
            .perform(get(ENTITY_API_URL).param("currentContractType.equals", "HDLD_XDTH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].code").value("NVA01"));
    }

    protected long getRepositoryCount() {
        return employeeRepository.count();
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

    protected Employee getPersistedEmployee(Employee employee) {
        return employeeRepository.findById(employee.getId()).orElseThrow();
    }

    protected void assertPersistedEmployeeToMatchAllProperties(Employee expectedEmployee) {
        assertEmployeeAllPropertiesEquals(expectedEmployee, getPersistedEmployee(expectedEmployee));
    }

    protected void assertPersistedEmployeeToMatchUpdatableProperties(Employee expectedEmployee) {
        assertEmployeeAllUpdatablePropertiesEquals(expectedEmployee, getPersistedEmployee(expectedEmployee));
    }
}
