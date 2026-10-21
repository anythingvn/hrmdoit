package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.doit.hrm.domain.WorkSiteAsserts.*;
import static vn.doit.hrm.web.rest.TestUtil.createUpdateProxyForBean;

import jakarta.persistence.EntityManager;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.dto.WorkSiteDTO;
import vn.doit.hrm.service.mapper.WorkSiteMapper;

/**
 * Integration tests for the {@link WorkSiteResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_HR")
class WorkSiteResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final WorkLocationType DEFAULT_TYPE = WorkLocationType.VAN_PHONG;
    private static final WorkLocationType UPDATED_TYPE = WorkLocationType.KHACH_HANG;

    private static final String DEFAULT_ADDRESS = "AAAAAAAAAA";
    private static final String UPDATED_ADDRESS = "BBBBBBBBBB";

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final String ENTITY_API_URL = "/api/work-sites";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    @Autowired
    private ObjectMapper om;

    @Autowired
    private WorkSiteRepository workSiteRepository;

    @Autowired
    private WorkSiteMapper workSiteMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restWorkSiteMockMvc;

    private WorkSite workSite;

    private WorkSite insertedWorkSite;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkSite createEntity() {
        return new WorkSite().name(DEFAULT_NAME).type(DEFAULT_TYPE).address(DEFAULT_ADDRESS).active(DEFAULT_ACTIVE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static WorkSite createUpdatedEntity() {
        return new WorkSite().name(UPDATED_NAME).type(UPDATED_TYPE).address(UPDATED_ADDRESS).active(UPDATED_ACTIVE);
    }

    @BeforeEach
    void initTest() {
        workSite = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedWorkSite != null) {
            workSiteRepository.delete(insertedWorkSite);
            insertedWorkSite = null;
        }
    }

    @Test
    @Transactional
    void createWorkSite() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);
        var returnedWorkSiteDTO = om.readValue(
            restWorkSiteMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            WorkSiteDTO.class
        );

        // Validate the WorkSite in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedWorkSite = workSiteMapper.toEntity(returnedWorkSiteDTO);
        assertWorkSiteUpdatableFieldsEquals(returnedWorkSite, getPersistedWorkSite(returnedWorkSite));

        insertedWorkSite = returnedWorkSite;
    }

    @Test
    @Transactional
    void createWorkSiteWithExistingId() throws Exception {
        // Create the WorkSite with an existing ID
        workSite.setId(1L);
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isBadRequest());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workSite.setName(null);

        // Create the WorkSite, which fails.
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workSite.setType(null);

        // Create the WorkSite, which fails.
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        workSite.setActive(null);

        // Create the WorkSite, which fails.
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    void getAllWorkSites() throws Exception {
        // Initialize the database
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);

        // Get all the workSiteList
        restWorkSiteMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(workSite.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].address").value(hasItem(DEFAULT_ADDRESS)))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)));
    }

    @Test
    @Transactional
    void getWorkSite() throws Exception {
        // Initialize the database
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);

        // Get the workSite
        restWorkSiteMockMvc
            .perform(get(ENTITY_API_URL_ID, workSite.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(workSite.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
            .andExpect(jsonPath("$.address").value(DEFAULT_ADDRESS))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE));
    }

    @Test
    @Transactional
    void getNonExistingWorkSite() throws Exception {
        // Get the workSite
        restWorkSiteMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingWorkSite() throws Exception {
        // Initialize the database
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workSite
        WorkSite updatedWorkSite = workSiteRepository.findById(workSite.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedWorkSite are not directly saved in db
        em.detach(updatedWorkSite);
        updatedWorkSite.name(UPDATED_NAME).type(UPDATED_TYPE).address(UPDATED_ADDRESS).active(UPDATED_ACTIVE);
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(updatedWorkSite);

        restWorkSiteMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workSiteDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workSiteDTO))
            )
            .andExpect(status().isOk());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedWorkSiteToMatchAllProperties(updatedWorkSite);
    }

    @Test
    @Transactional
    void putNonExistingWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(
                put(ENTITY_API_URL_ID, workSiteDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workSiteDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(workSiteDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateWorkSiteWithPatch() throws Exception {
        // Initialize the database
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workSite using partial update
        WorkSite partialUpdatedWorkSite = new WorkSite();
        partialUpdatedWorkSite.setId(workSite.getId());

        restWorkSiteMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkSite.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkSite))
            )
            .andExpect(status().isOk());

        // Validate the WorkSite in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkSiteUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedWorkSite, workSite), getPersistedWorkSite(workSite));
    }

    @Test
    @Transactional
    void fullUpdateWorkSiteWithPatch() throws Exception {
        // Initialize the database
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the workSite using partial update
        WorkSite partialUpdatedWorkSite = new WorkSite();
        partialUpdatedWorkSite.setId(workSite.getId());

        partialUpdatedWorkSite.name(UPDATED_NAME).type(UPDATED_TYPE).address(UPDATED_ADDRESS).active(UPDATED_ACTIVE);

        restWorkSiteMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedWorkSite.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedWorkSite))
            )
            .andExpect(status().isOk());

        // Validate the WorkSite in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertWorkSiteUpdatableFieldsEquals(partialUpdatedWorkSite, getPersistedWorkSite(partialUpdatedWorkSite));
    }

    @Test
    @Transactional
    void patchNonExistingWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, workSiteDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workSiteDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(workSiteDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamWorkSite() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        workSite.setId(longCount.incrementAndGet());

        // Create the WorkSite
        WorkSiteDTO workSiteDTO = workSiteMapper.toDto(workSite);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restWorkSiteMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(workSiteDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the WorkSite in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void catalogSeeded() throws Exception {
        var seeded = workSiteRepository.findAll().stream().filter(w -> "Văn phòng".equals(w.getName())).findFirst().orElseThrow();
        assertThat(seeded.getType()).isEqualTo(WorkLocationType.VAN_PHONG);
        assertThat(seeded.getActive()).isTrue();
        // Khách hàng mẫu ban đầu đã bỏ: danh mục khách hàng do HR nhập dần khi điều động.
        for (String name : new String[] { "Việt Á", "SeAbank" }) {
            assertThat(workSiteRepository.findAll().stream().filter(w -> name.equals(w.getName()) && Boolean.TRUE.equals(w.getActive()))).isEmpty();
        }
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanCreate() throws Exception {
        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteMapper.toDto(workSite))))
            .andExpect(status().isCreated());
    }

    @Test
    @Transactional
    void hrCanCreate() throws Exception {
        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteMapper.toDto(workSite))))
            .andExpect(status().isCreated());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCannotCreate() throws Exception {
        restWorkSiteMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(workSiteMapper.toDto(workSite))))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = "ROLE_KE_TOAN")
    void accountantCanRead() throws Exception {
        restWorkSiteMockMvc.perform(get(ENTITY_API_URL)).andExpect(status().isOk());
    }

    @Test
    @Transactional
    void deleteNotAllowed() throws Exception {
        insertedWorkSite = workSiteRepository.saveAndFlush(workSite);
        restWorkSiteMockMvc
            .perform(delete(ENTITY_API_URL_ID, workSite.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isMethodNotAllowed());
    }

    protected long getRepositoryCount() {
        return workSiteRepository.count();
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

    protected WorkSite getPersistedWorkSite(WorkSite workSite) {
        return workSiteRepository.findById(workSite.getId()).orElseThrow();
    }

    protected void assertPersistedWorkSiteToMatchAllProperties(WorkSite expectedWorkSite) {
        assertWorkSiteAllPropertiesEquals(expectedWorkSite, getPersistedWorkSite(expectedWorkSite));
    }

    protected void assertPersistedWorkSiteToMatchUpdatableProperties(WorkSite expectedWorkSite) {
        assertWorkSiteAllUpdatablePropertiesEquals(expectedWorkSite, getPersistedWorkSite(expectedWorkSite));
    }
}
