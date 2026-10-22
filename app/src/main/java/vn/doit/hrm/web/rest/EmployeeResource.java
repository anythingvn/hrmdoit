package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeAccountService;
import vn.doit.hrm.service.EmployeeArchiveService;
import vn.doit.hrm.service.EmployeeExcelExporter;
import vn.doit.hrm.service.EmployeeQueryService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.EmployeeService;
import vn.doit.hrm.service.TerminationService;
import vn.doit.hrm.service.WorkAssignmentService;
import vn.doit.hrm.service.criteria.EmployeeCriteria;
import vn.doit.hrm.service.dto.ArchiveResultDTO;
import vn.doit.hrm.service.dto.ArchivedEmployeeDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.EmployeeSensitiveDTO;
import vn.doit.hrm.service.dto.TerminationRequestDTO;
import vn.doit.hrm.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing {@link vn.doit.hrm.domain.Employee}.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeResource.class);

    private static final String ENTITY_NAME = "employee";

    @Value("${jhipster.clientApp.name:hrm}")
    private String applicationName;

    private final EmployeeService employeeService;

    private final EmployeeRepository employeeRepository;

    private final EmployeeQueryService employeeQueryService;

    private final TerminationService terminationService;

    private final EmployeeExcelExporter employeeExcelExporter;

    private final EmployeeAccountService employeeAccountService;

    private final WorkAssignmentService workAssignmentService;

    private final EmployeeArchiveService employeeArchiveService;

    public EmployeeResource(
        EmployeeService employeeService,
        EmployeeRepository employeeRepository,
        EmployeeQueryService employeeQueryService,
        TerminationService terminationService,
        EmployeeExcelExporter employeeExcelExporter,
        EmployeeAccountService employeeAccountService,
        WorkAssignmentService workAssignmentService,
        EmployeeArchiveService employeeArchiveService
    ) {
        this.employeeArchiveService = employeeArchiveService;
        this.workAssignmentService = workAssignmentService;
        this.employeeAccountService = employeeAccountService;
        this.employeeExcelExporter = employeeExcelExporter;
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.employeeQueryService = employeeQueryService;
        this.terminationService = terminationService;
    }

    /**
     * {@code POST  /employees} : Create a new employee.
     *
     * @param employeeDTO the employeeDTO to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new employeeDTO, or with status {@code 400 (Bad Request)} if the employee already has an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    /** {@code GET /employees/next-code?personType=&joinDate=} : mã gợi ý kế tiếp (cộng dồn) cho hồ sơ mới; HĐ dịch vụ không có. */
    @PreAuthorize(
        "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "', '" + AuthoritiesConstants.KE_TOAN + "')"
    )
    @GetMapping("/next-code")
    public java.util.Map<String, String> nextCode(
        @RequestParam(required = false) vn.doit.hrm.domain.enumeration.PersonType personType,
        @RequestParam(required = false) java.time.LocalDate joinDate
    ) {
        return employeeService.nextCode(personType, joinDate).map(c -> java.util.Map.of("code", c)).orElse(java.util.Map.of());
    }

    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("")
    public ResponseEntity<EmployeeDTO> createEmployee(@Valid @RequestBody EmployeeDTO employeeDTO) throws URISyntaxException {
        LOG.debug("REST request to save Employee : {}", employeeDTO);
        if (employeeDTO.getId() != null) {
            throw new BadRequestAlertException("A new employee cannot already have an ID", ENTITY_NAME, "idexists");
        }
        try {
            employeeDTO = employeeService.save(employeeDTO);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
        return ResponseEntity.created(new URI("/api/employees/" + employeeDTO.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, employeeDTO.getId().toString()))
            .body(employeeDTO);
    }

    /**
     * {@code PUT  /employees/:id} : Updates an existing employee.
     *
     * @param id the id of the employeeDTO to save.
     * @param employeeDTO the employeeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employeeDTO,
     * or with status {@code 400 (Bad Request)} if the employeeDTO is not valid,
     * or with status {@code 500 (Internal Server Error)} if the employeeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeDTO> updateEmployee(
        @PathVariable(value = "id", required = false) final Long id,
        @Valid @RequestBody EmployeeDTO employeeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to update Employee : {}, {}", id, employeeDTO);
        if (employeeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employeeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        try {
            employeeDTO = employeeService.update(employeeDTO);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employeeDTO.getId().toString()))
            .body(employeeDTO);
    }

    /**
     * {@code PATCH  /employees/:id} : Partial updates given fields of an existing employee, field will ignore if it is null
     *
     * @param id the id of the employeeDTO to save.
     * @param employeeDTO the employeeDTO to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated employeeDTO,
     * or with status {@code 400 (Bad Request)} if the employeeDTO is not valid,
     * or with status {@code 404 (Not Found)} if the employeeDTO is not found,
     * or with status {@code 500 (Internal Server Error)} if the employeeDTO couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PatchMapping(value = "/{id}", consumes = { "application/json", "application/merge-patch+json" })
    public ResponseEntity<EmployeeDTO> partialUpdateEmployee(
        @PathVariable(value = "id", required = false) final Long id,
        @NotNull @RequestBody EmployeeDTO employeeDTO
    ) throws URISyntaxException {
        LOG.debug("REST request to partially update Employee : {}, {}", id, employeeDTO);
        if (employeeDTO.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!Objects.equals(id, employeeDTO.getId())) {
            throw new BadRequestAlertException("Invalid ID", ENTITY_NAME, "idinvalid");
        }

        if (!employeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }

        Optional<EmployeeDTO> result;
        try {
            result = employeeService.partialUpdate(employeeDTO);
        } catch (EmployeeRuleException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        }

        return ResponseUtil.wrapOrNotFound(
            result,
            HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, employeeDTO.getId().toString())
        );
    }

    /**
     * {@code GET  /employees} : get all the Employees.
     *
     * @param pageable the pagination information.
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Employees in body.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    @GetMapping("")
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees(
        EmployeeCriteria criteria,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get Employees by criteria: {}", criteria);
        checkSensitiveFilters(criteria);
        checkSortable(pageable.getSort(), LIST_SORTABLE);

        Page<EmployeeDTO> page = employeeQueryService.findByCriteria(criteria, pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /** Trường được phép sắp xếp khi xuất Excel: không có trường nhạy cảm (CCCD, MST, tài khoản, BHXH…). */
    private static final java.util.Set<String> EXPORT_SORTABLE = java.util.Set.of(
        "id",
        "code",
        "fullName",
        "gender",
        "birthDate",
        "email",
        "phone",
        "position",
        "status",
        "joinDate",
        "personType"
    );

    /** Danh sách: như xuất Excel, thêm tên phòng ban; mọi trường khác (kể cả lồng như manager.idNumber) bị từ chối. */
    private static final java.util.Set<String> LIST_SORTABLE = java.util.Set.copyOf(
        java.util.stream.Stream.concat(EXPORT_SORTABLE.stream(), java.util.stream.Stream.of("department.name")).toList()
    );

    /** Sắp xếp theo trường không có trong danh sách cho phép → 400 {@code badsort}. */
    private static void checkSortable(org.springframework.data.domain.Sort sort, java.util.Set<String> allowed) {
        for (org.springframework.data.domain.Sort.Order o : sort) {
            if (!allowed.contains(o.getProperty())) {
                throw new BadRequestAlertException("Không thể sắp xếp theo " + o.getProperty(), ENTITY_NAME, "badsort");
            }
        }
    }

    /**
     * {@code GET  /employees/export.xlsx} : xuất danh sách nhân viên (cùng bộ lọc với danh sách, không phân trang).
     * Cột lương chỉ có với ADMIN/HR/BGD. Không sắp xếp theo trường nhạy cảm.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportEmployees(EmployeeCriteria criteria, org.springframework.data.domain.Sort sort) {
        LOG.debug("REST request to export Employees by criteria: {}", criteria);
        checkSensitiveFilters(criteria);
        checkSortable(sort, EXPORT_SORTABLE);
        if (sort.isUnsorted()) {
            sort = org.springframework.data.domain.Sort.by("code");
        }
        boolean salary = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.HR,
            AuthoritiesConstants.BGD
        );
        byte[] body = employeeExcelExporter.export(criteria, sort, salary);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nhan-vien.xlsx\"")
            .body(body);
    }

    /**
     * {@code GET  /employees/count} : count all the employees.
     *
     * @param criteria the criteria which the requested entities should match.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the count in body.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    @GetMapping("/count")
    public ResponseEntity<Long> countEmployees(EmployeeCriteria criteria) {
        LOG.debug("REST request to count Employees by criteria: {}", criteria);
        checkSensitiveFilters(criteria);
        return ResponseEntity.ok().body(employeeQueryService.countByCriteria(criteria));
    }

    /**
     * {@code GET  /employees/:id} : get the "id" employee.
     *
     * @param id the id of the employeeDTO to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the employeeDTO, or with status {@code 404 (Not Found)}.
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDTO> getEmployee(@PathVariable("id") Long id) {
        LOG.debug("REST request to get Employee : {}", id);
        Optional<EmployeeDTO> employeeDTO = employeeService.findOne(id);
        return ResponseUtil.wrapOrNotFound(employeeDTO);
    }

    /**
     * {@code POST  /employees/:id/terminate} : cho nghỉ việc. Ngày làm việc cuối đã qua: áp dụng ngay (đặt {@code DA_NGHI}, chốt vị trí
     * đang mở và kết thúc HĐ đang hiệu lực về ngày làm việc cuối, khóa tài khoản; không xóa gì). Từ hôm nay trở đi: hẹn lịch,
     * hồ sơ trả về có {@code scheduledTermination}.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("/{id}/terminate")
    public ResponseEntity<EmployeeDTO> terminateEmployee(@PathVariable("id") Long id, @RequestBody TerminationRequestDTO request) {
        LOG.debug("REST request to terminate Employee : {}, {}", id, request);
        checkExists(id);
        try {
            terminationService.terminate(id, request);
        } catch (EmployeeRuleException e) {
            throw ruleError(e);
        }
        return ResponseUtil.wrapOrNotFound(employeeService.findOne(id));
    }

    /** {@code DELETE  /employees/:id/termination} : hủy lịch nghỉ việc chưa áp dụng (không đổi gì khác). */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @DeleteMapping("/{id}/termination")
    public ResponseEntity<EmployeeDTO> cancelTermination(@PathVariable("id") Long id) {
        LOG.debug("REST request to cancel scheduled termination of Employee : {}", id);
        checkExists(id);
        try {
            terminationService.cancel(id);
        } catch (EmployeeRuleException e) {
            throw ruleError(e);
        }
        return ResponseUtil.wrapOrNotFound(employeeService.findOne(id));
    }

    /**
     * {@code POST  /employees/:id/termination/undo} : hoàn tác lần cho nghỉ việc gần nhất (trả lại đúng giá trị cũ, mở lại tài khoản).
     * Không có gì để hoàn tác → 400 {@code nothingtoundo}; dữ liệu đã bị sửa tiếp → 400 {@code undoconflict} ({@code detail} liệt kê).
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("/{id}/termination/undo")
    public ResponseEntity<EmployeeDTO> undoTermination(@PathVariable("id") Long id) {
        LOG.debug("REST request to undo termination of Employee : {}", id);
        checkExists(id);
        String notice;
        try {
            notice = terminationService.undo(id);
        } catch (EmployeeRuleException e) {
            throw ruleError(e);
        }
        return ResponseUtil.wrapOrNotFound(
            employeeService
                .findOne(id)
                .map(dto -> {
                    dto.setTerminationNotice(notice);
                    return dto;
                })
        );
    }

    /**
     * {@code GET  /employees/:id/termination/blockers?lastWorkingDay=} : các giai đoạn làm việc bắt đầu sau ngày làm việc cuối
     * (chặn việc cho nghỉ), kèm cờ {@code autoReturn} cho giai đoạn "quay về" hệ thống tự tạo.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @GetMapping("/{id}/termination/blockers")
    public java.util.List<vn.doit.hrm.service.dto.TerminationBlockerDTO> terminationBlockers(
        @PathVariable("id") Long id,
        @RequestParam("lastWorkingDay") java.time.LocalDate lastWorkingDay
    ) {
        checkExists(id);
        return workAssignmentService.blockers(id, lastWorkingDay);
    }

    /** Nội dung {@code POST /employees/:id/archive}: ghi chú không bắt buộc. */
    public record ArchiveRequest(@jakarta.validation.constraints.Size(max = 500) String note) {}

    /**
     * {@code POST  /employees/:id/archive} : đưa hồ sơ Đã nghỉ (hoặc hồ sơ tạo nhầm) vào lưu trữ. Trả những người và phòng ban còn
     * nhận hồ sơ này làm quản lý/trưởng phòng.
     */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("/{id}/archive")
    public ArchiveResultDTO archiveEmployee(@PathVariable("id") Long id, @Valid @RequestBody(required = false) ArchiveRequest request) {
        LOG.debug("REST request to archive Employee : {}", id);
        checkExists(id);
        try {
            return employeeArchiveService.archive(id, request == null ? null : request.note());
        } catch (EmployeeRuleException e) {
            throw ruleError(e);
        }
    }

    /** {@code POST  /employees/:id/restore} : đưa hồ sơ từ vùng lưu trữ về danh sách (không đổi gì khác). */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @PostMapping("/{id}/restore")
    public ResponseEntity<EmployeeDTO> restoreEmployee(@PathVariable("id") Long id) {
        LOG.debug("REST request to restore Employee : {}", id);
        checkExists(id);
        try {
            employeeArchiveService.restore(id);
        } catch (EmployeeRuleException e) {
            throw ruleError(e);
        }
        return ResponseUtil.wrapOrNotFound(employeeService.findOne(id));
    }

    /** {@code GET  /employees/archived?q=&page=} : vùng lưu trữ, tìm theo mã, họ tên, CCCD; mới lưu trữ trước. */
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    @GetMapping("/archived")
    public ResponseEntity<List<ArchivedEmployeeDTO>> archivedEmployees(
        @RequestParam(value = "q", required = false) String q,
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        Pageable newestFirst = org.springframework.data.domain.PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("archivedAt"),
                org.springframework.data.domain.Sort.Order.asc("code")
            )
        );
        Page<ArchivedEmployeeDTO> page = employeeArchiveService.archived(q, newestFirst);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /** Lỗi quy tắc → 400 với mã lỗi và câu tiếng Việt đầy đủ trong {@code detail}. */
    private static BadRequestAlertException ruleError(EmployeeRuleException e) {
        BadRequestAlertException ex = new BadRequestAlertException(e.getMessage(), ENTITY_NAME, e.getErrorKey());
        ex.getBody().setDetail(e.getMessage());
        return ex;
    }

    private void checkExists(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
    }

    /** {@code GET  /employees/:id/contract-advice} : lời khuyên khi tạo hợp đồng kế tiếp. */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "', '" +
        AuthoritiesConstants.BGD +
        "')"
    )
    @GetMapping("/{id}/contract-advice")
    public java.util.Map<String, Boolean> getContractAdvice(@PathVariable("id") Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new BadRequestAlertException("Entity not found", ENTITY_NAME, "idnotfound");
        }
        return java.util.Map.of("nextMustBeIndefinite", employeeService.nextContractMustBeIndefinite(id));
    }

    /**
     * {@code GET  /employees/:id/sensitive} : dữ liệu nhạy cảm của nhân viên; mỗi lần xem thành công được ghi nhật ký.
     * ADMIN/HR xem đủ; KE_TOAN chỉ thấy tài khoản ngân hàng và số BHXH; ai cũng xem đủ hồ sơ của chính mình
     * (gồm ROLE_NHAN_VIEN, vai trò chỉ được xem hồ sơ của mình).
     */
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR +
        "', '" +
        AuthoritiesConstants.KE_TOAN +
        "') or @employeeAccountService.isOwnProfile(#id)"
    )
    @GetMapping("/{id}/sensitive")
    public ResponseEntity<EmployeeSensitiveDTO> getEmployeeSensitive(@PathVariable("id") Long id) {
        // Hồ sơ của chính mình thì xem đủ.
        boolean full =
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR) ||
            employeeAccountService.isOwnProfile(id);
        String viewer = SecurityUtils.getCurrentUserLogin().orElse("unknown");
        LOG.debug("REST request to get sensitive data of Employee : {} by {}", id, viewer);
        return ResponseUtil.wrapOrNotFound(employeeService.findSensitive(id, full, viewer));
    }

    /** Lọc theo trường nhạy cảm chỉ dành cho người được phép xem trường đó, tránh dò giá trị qua bộ lọc. */
    private static void checkSensitiveFilters(EmployeeCriteria c) {
        boolean idGroup = c.getIdNumber() != null || c.getIdIssueDate() != null || c.getIdIssuePlace() != null || c.getTaxCode() != null;
        boolean payGroup = c.getBankAccount() != null || c.getBankName() != null || c.getSocialInsuranceNo() != null;
        if (idGroup && !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR)) {
            throw new AccessDeniedException("Không có quyền lọc theo dữ liệu nhạy cảm");
        }
        if (
            payGroup &&
            !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR, AuthoritiesConstants.KE_TOAN)
        ) {
            throw new AccessDeniedException("Không có quyền lọc theo dữ liệu nhạy cảm");
        }
    }
}
