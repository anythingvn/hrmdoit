package vn.doit.hrm.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDeletionLog;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.repository.EmployeeDeletionLogRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.leave.LeaveForbiddenException;
import vn.doit.hrm.service.leave.LeaveRequestService;
import vn.doit.hrm.service.leave.OvertimeService;

/**
 * Xóa hẳn một nhân viên đã lưu trữ (chỉ login {@code admin}), trong một giao dịch:
 * <ul>
 *   <li>dòng lương của mọi kỳ giữ nguyên số tiền, {@code file_code}, {@code file_name}; chỉ {@code employee_id} thành trống;</li>
 *   <li>tham chiếu từ người khác (quản lý, trưởng phòng, người duyệt đơn) thành trống, {@code decided_by} giữ nguyên;</li>
 *   <li>mọi bản ghi con, dòng {@code _aud} của hồ sơ và bản ghi con, tài khoản đăng nhập (trừ {@code admin}) bị xóa;</li>
 *   <li>ghi một dòng nhật ký xóa (không có CCCD, ngân hàng, địa chỉ).</li>
 * </ul>
 * File tài liệu và văn bản đã sinh chỉ bị xóa khỏi đĩa sau khi giao dịch thành công; lỗi xóa file chỉ ghi log.
 */
@Service
@Transactional
public class EmployeeDeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDeletionService.class);

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    /**
     * Mọi bảng có khóa ngoại tới {@code employee} mà dịch vụ này xử lý (xóa dòng hoặc đặt trống cột). Test
     * {@code EmployeeForeignKeyCoverageIT} đọc khóa ngoại từ CSDL thật: thêm bảng mới trỏ tới nhân viên mà quên xử lý ở đây thì test đỏ.
     */
    public static final Set<String> HANDLED_TABLES = Set.of(
        "employee",
        "department",
        "employment_contract",
        "salary_change",
        "allowance",
        "work_assignment",
        "employee_document",
        "termination_record",
        "assignment_decision",
        "payroll_import_mapping",
        "payroll_line",
        "payslip_delivery",
        "leave_ledger_entry",
        "leave_request",
        "overtime_request",
        "employment_period",
        "work_site_onsite_lead",
        "attendance_punch",
        "attendance_mark",
        "attendance_snapshot",
        "attendance_consent",
        "attendance_explanation"
    );

    /** Cột khóa ngoại tới {@code employee} được đặt trống (dòng giữ lại). */
    public static final Set<String> NULLED_COLUMNS = Set.of(
        "employee.manager_id",
        "department.head_id",
        "leave_request.approver_employee_id",
        "overtime_request.approver_employee_id",
        "payroll_line.employee_id"
    );

    /** Cột khóa ngoại tới {@code employee} mà dòng của người bị xóa bị xóa theo. */
    public static final Set<String> DELETED_COLUMNS = Set.of(
        "employment_contract.employee_id",
        "salary_change.employee_id",
        "allowance.employee_id",
        "work_assignment.employee_id",
        "employee_document.employee_id",
        "termination_record.employee_id",
        "assignment_decision.employee_id",
        "payroll_import_mapping.employee_id",
        "payslip_delivery.employee_id",
        "leave_ledger_entry.employee_id",
        "leave_request.employee_id",
        "overtime_request.employee_id",
        "employment_period.employee_id",
        "work_site_onsite_lead.employee_id",
        "attendance_punch.employee_id",
        "attendance_mark.employee_id",
        "attendance_snapshot.employee_id",
        "attendance_consent.employee_id",
        "attendance_explanation.employee_id"
    );

    /** Bảng có {@code employee_id} (kể cả không có khóa ngoại) bị xóa theo người, theo thứ tự lá → gốc. */
    static final List<String> DELETE_ORDER = List.of(
        "overtime_request",
        "employment_period",
        "termination_record",
        "assignment_decision",
        "employment_contract",
        "salary_change",
        "allowance",
        "work_assignment",
        "employee_document",
        "payroll_import_mapping",
        "payslip_delivery",
        "sensitive_access_log",
        "work_site_onsite_lead",
        "attendance_punch",
        "attendance_mark",
        "attendance_snapshot",
        "attendance_consent",
        "attendance_explanation"
    );

    /** Bảng Envers ({@code <bảng>_aud} có cột {@code employee_id}) của các bản ghi con bị xóa. */
    public static final List<String> AUDITED_CHILDREN = List.of(
        "employment_contract",
        "salary_change",
        "allowance",
        "work_assignment",
        "employee_document",
        "termination_record",
        "assignment_decision",
        "payroll_import_mapping",
        "payslip_delivery",
        "leave_request",
        "overtime_request",
        "employment_period",
        "attendance_punch",
        "attendance_mark",
        "attendance_explanation"
    );

    private final EmployeeRepository employeeRepository;

    private final EmployeeDeletionLogRepository logRepository;

    private final LeaveLedgerEntryRepository ledgerRepository;

    private final NamedParameterJdbcTemplate jdbc;

    private final EntityManager entityManager;

    private final FileStorageService fileStorage;

    private final Clock clock;

    private final LeaveRequestService leaveRequestService;

    private final OvertimeService overtimeService;

    public EmployeeDeletionService(
        EmployeeRepository employeeRepository,
        EmployeeDeletionLogRepository logRepository,
        LeaveLedgerEntryRepository ledgerRepository,
        NamedParameterJdbcTemplate jdbc,
        EntityManager entityManager,
        FileStorageService fileStorage,
        Clock clock,
        LeaveRequestService leaveRequestService,
        OvertimeService overtimeService
    ) {
        this.leaveRequestService = leaveRequestService;
        this.overtimeService = overtimeService;
        this.employeeRepository = employeeRepository;
        this.logRepository = logRepository;
        this.ledgerRepository = ledgerRepository;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.fileStorage = fileStorage;
        this.clock = clock;
    }

    /**
     * Xóa hẳn hồ sơ {@code id}. Lỗi: 403 {@code deleterequiressystemadmin}; 400 {@code deletereasonrequired},
     * {@code deletenotarchived}, {@code deleteadminaccount}, {@code deleteconfirmmismatch}, {@code deleteblocked} (kèm lý do).
     */
    public void delete(Long id, String confirmCode, String reason) {
        String actor = requireSystemAdmin();
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new EmployeeRuleException("Hãy nhập lý do xóa hẳn", "deletereasonrequired");
        }
        if (why.length() > 500) {
            throw new EmployeeRuleException("Lý do tối đa 500 ký tự", "deletereasontoolong");
        }
        Employee employee = employeeRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        if (!employee.isArchived()) {
            throw new EmployeeRuleException("Chỉ xóa hẳn được hồ sơ đang ở vùng lưu trữ", "deletenotarchived");
        }
        if (employee.getUser() != null && IdNumbers.SYSTEM_ADMIN_LOGIN.equals(employee.getUser().getLogin())) {
            throw new EmployeeRuleException("Không xóa được hồ sơ gắn tài khoản admin", "deleteadminaccount");
        }
        if (confirmCode == null || !confirmCode.trim().equals(employee.getCode())) {
            throw new EmployeeRuleException("Mã nhân viên xác nhận không khớp", "deleteconfirmmismatch");
        }
        List<String> blockers = blockers(id);
        if (!blockers.isEmpty()) {
            throw new EmployeeRuleException("Chưa xóa hẳn được: " + String.join("; ", blockers), "deleteblocked");
        }

        String code = employee.getCode();
        String fullName = employee.getFullName();
        Long userId = employee.getUser() == null ? null : employee.getUser().getId();
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);

        // Thu thập trước khi xóa: id bản ghi con để xóa _aud, đường dẫn file để xóa sau khi giao dịch xong.
        // childIds: bản ghi của người này (đang có, hoặc đã xóa trước đó mà còn lịch sử) → xóa toàn bộ lịch sử của bản ghi.
        // sharedIds: bản ghi từng gắn người này nhưng nay thuộc người khác (vd dòng gắn bảng lương được dùng lại khi gắn lại) →
        // chỉ xóa các dòng lịch sử có employee_id của người này, giữ phần lịch sử còn lại.
        Map<String, Set<Long>> childIds = new LinkedHashMap<>();
        Map<String, Set<Long>> sharedIds = new LinkedHashMap<>();
        for (String table : AUDITED_CHILDREN) {
            Set<Long> ids = new LinkedHashSet<>(jdbc.queryForList("select id from " + table + " where employee_id = :id", p, Long.class));
            Set<Long> shared = new LinkedHashSet<>(
                jdbc.queryForList(
                    "select distinct a.id from " +
                        table +
                        "_aud a join " +
                        table +
                        " b on b.id = a.id " +
                        "where a.employee_id = :id and b.employee_id <> :id",
                    p,
                    Long.class
                )
            );
            for (Long audId : jdbc.queryForList("select distinct id from " + table + "_aud where employee_id = :id", p, Long.class)) {
                if (!shared.contains(audId)) {
                    ids.add(audId);
                }
            }
            childIds.put(table, ids);
            sharedIds.put(table, shared);
        }
        Set<String> emails = new LinkedHashSet<>();
        for (String email : new String[] { employee.getEmail(), employee.getUser() == null ? null : employee.getUser().getEmail() }) {
            if (email != null && !email.isBlank()) {
                emails.add(email.trim().toLowerCase(Locale.ROOT));
            }
        }
        Set<Long> generatedIds = generatedDocumentIds(childIds.get("employment_contract"), childIds.get("assignment_decision"));
        List<String> files = new ArrayList<>();
        files.addAll(jdbc.queryForList("select storage_path from employee_document where employee_id = :id", p, String.class));
        if (!generatedIds.isEmpty()) {
            files.addAll(
                jdbc.queryForList(
                    "select storage_path from generated_document where id in (:ids)",
                    new MapSqlParameterSource("ids", generatedIds),
                    String.class
                )
            );
        }

        // Tham chiếu từ người khác → trống (qua JPA để lịch sử của họ ghi lại thay đổi).
        clearReferencesFromOthers(id, fullName);
        entityManager.flush();
        entityManager.clear();

        // Dòng lương giữ lại, chỉ bỏ liên kết.
        jdbc.update("update payroll_line set employee_id = null where employee_id = :id", p);
        jdbc.update("update payroll_line_aud set employee_id = null where employee_id = :id", p);

        // Bản ghi con, lá → gốc.
        jdbc.update(
            "delete from leave_request_termination_change where leave_request_id in (select id from leave_request where employee_id = :id) " +
                "or termination_record_id in (select id from termination_record where employee_id = :id)",
            p
        );
        ledgerRepository.purgeByEmployeeId(id);
        jdbc.update(
            "update leave_request set sick_doc_id = null where employee_id <> :id and sick_doc_id in (select id from employee_document where employee_id = :id)",
            p
        );
        jdbc.update("delete from leave_request where employee_id = :id", p);
        if (!generatedIds.isEmpty()) {
            MapSqlParameterSource g = new MapSqlParameterSource("ids", generatedIds);
            jdbc.update("update generated_document set source_document_id = null where source_document_id in (:ids)", g);
            jdbc.update("delete from generated_document where id in (:ids)", g);
        }
        for (String table : DELETE_ORDER) {
            jdbc.update("delete from " + table + " where employee_id = :id", p);
        }

        // Lịch sử thay đổi (Envers) của hồ sơ và mọi bản ghi con.
        for (Map.Entry<String, Set<Long>> e : childIds.entrySet()) {
            if (!e.getValue().isEmpty()) {
                jdbc.update("delete from " + e.getKey() + "_aud where id in (:ids)", new MapSqlParameterSource("ids", e.getValue()));
            }
        }
        for (Map.Entry<String, Set<Long>> e : sharedIds.entrySet()) {
            if (!e.getValue().isEmpty()) {
                jdbc.update(
                    "delete from " + e.getKey() + "_aud where id in (:ids) and employee_id = :id",
                    new MapSqlParameterSource("ids", e.getValue()).addValue("id", id)
                );
            }
        }
        if (!generatedIds.isEmpty()) {
            jdbc.update("delete from generated_document_aud where id in (:ids)", new MapSqlParameterSource("ids", generatedIds));
        }
        jdbc.update("delete from employee_aud where id = :id", p);
        // Nhắc việc đã gửi tới email của người này (dữ liệu cá nhân).
        // Bỏ qua hộp thư còn dùng chung với hồ sơ hoặc tài khoản khác (R25): nhắc việc đó không chỉ là dữ liệu của người này.
        if (!emails.isEmpty()) {
            MapSqlParameterSource m = new MapSqlParameterSource("emails", emails).addValue("id", id).addValue("uid", userId == null ? -1L : userId);
            jdbc.update(
                "delete from reminder_delivery r where lower(trim(r.email)) in (:emails)" +
                " and not exists (select 1 from employee o where o.id <> :id and lower(trim(o.email)) = lower(trim(r.email)))" +
                " and not exists (select 1 from jhi_user u where u.id <> :uid" +
                " and lower(trim(u.email)) = lower(trim(r.email)))",
                m
            );
        }

        // Hồ sơ, rồi tài khoản đã gắn (không bao giờ là admin – đã chặn ở trên).
        jdbc.update("update employee set manager_id = null where id = :id", p);
        jdbc.update("delete from employee where id = :id", p);
        if (userId != null) {
            MapSqlParameterSource u = new MapSqlParameterSource("uid", userId).addValue("admin", IdNumbers.SYSTEM_ADMIN_LOGIN);
            jdbc.update("delete from jhi_user_authority where user_id = :uid", u);
            jdbc.update("delete from jhi_user where id = :uid and login <> :admin", u);
        }

        logRepository.save(new EmployeeDeletionLog(Instant.now(clock), actor, why, code, fullName));
        deleteFilesAfterCommit(files);
        LOG.info("Employee {} ({}) permanently deleted by {}", id, code, actor);
    }

    /** Nhật ký xóa hẳn, mới nhất trước; chỉ login admin. */
    @Transactional(readOnly = true)
    public Page<EmployeeDeletionLog> log(Pageable pageable) {
        requireSystemAdmin();
        return logRepository.findAllByOrderByDeletedAtDescIdDesc(pageable);
    }

    /** Login hiện tại phải là {@code admin} (có vai trò Quản trị); người khác, kể cả Quản trị khác → 403. */
    public static String requireSystemAdmin() {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!IdNumbers.SYSTEM_ADMIN_LOGIN.equals(login) || !SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN)) {
            throw new LeaveForbiddenException("Chỉ tài khoản admin được xóa hẳn nhân viên và xem nhật ký xóa", "deleterequiressystemadmin");
        }
        return login;
    }

    /** Lý do chặn: kỳ lương chưa duyệt, bản nháp import đang làm có dòng gắn người này, lượt gửi phiếu lương đang chờ. */
    List<String> blockers(Long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        List<String> reasons = new ArrayList<>();
        for (String period : jdbc.queryForList(
            "select distinct pp.period from payroll_line l join payroll_version v on v.id = l.version_id " +
                "join payroll_period pp on pp.id = v.period_id where l.employee_id = :id and pp.status <> 'DA_DUYET' order by pp.period",
            p,
            String.class
        )) {
            reasons.add("Kỳ lương " + month(period) + " chưa duyệt có dòng của người này");
        }
        for (String period : jdbc.queryForList(
            "select distinct i.period from payroll_import_mapping m join payroll_import i on i.id = m.import_id " +
                "where m.employee_id = :id and i.status = 'DANG_LAM' order by i.period",
            p,
            String.class
        )) {
            reasons.add("Bản nháp import bảng lương " + month(period) + " đang làm có dòng gắn người này");
        }
        for (String period : jdbc.queryForList(
            "select distinct pp.period from payslip_delivery d join payroll_period pp on pp.id = d.period_id " +
                "where d.employee_id = :id and d.status = 'PENDING' order by pp.period",
            p,
            String.class
        )) {
            reasons.add("Phiếu lương " + month(period) + " đang chờ gửi");
        }
        return reasons;
    }

    private static String month(String period) {
        try {
            return YearMonth.parse(period).format(MONTH);
        } catch (DateTimeParseException e) {
            return period;
        }
    }

    private Set<Long> generatedDocumentIds(Collection<Long> contractIds, Collection<Long> decisionIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String table : List.of("generated_document", "generated_document_aud")) {
            if (!contractIds.isEmpty()) {
                ids.addAll(
                    jdbc.queryForList(
                        "select id from " + table + " where owner_type = 'CONTRACT' and owner_id in (:ids)",
                        new MapSqlParameterSource("ids", contractIds),
                        Long.class
                    )
                );
            }
            if (!decisionIds.isEmpty()) {
                ids.addAll(
                    jdbc.queryForList(
                        "select id from " + table + " where owner_type = 'DECISION' and owner_id in (:ids)",
                        new MapSqlParameterSource("ids", decisionIds),
                        Long.class
                    )
                );
            }
        }
        return ids;
    }

    private void clearReferencesFromOthers(Long id, String fullName) {
        entityManager
            .createQuery("select e from Employee e where e.manager.id = :id and e.id <> :id", Employee.class)
            .setParameter("id", id)
            .getResultList()
            .forEach(e -> e.setManager(null));
        entityManager
            .createQuery("select d from Department d where d.head.id = :id", Department.class)
            .setParameter("id", id)
            .getResultList()
            .forEach(d -> d.setHead(null));
        // Quản lý/trưởng phòng bỏ trước để cấp duyệt kế tiếp không rơi lại vào người bị xóa (hồ sơ đã lưu trữ cũng không được chọn).
        entityManager.flush();
        // Đơn đang chờ của người khác: chuyển lên cấp duyệt kế tiếp như khi người duyệt nghỉ việc, không để trống người duyệt.
        leaveRequestService.reassignAllFrom(id, fullName + " đã bị xóa hẳn");
        overtimeService.reassignFromApprover(id);
        entityManager.flush();
        // Đơn đã quyết định: chỉ bỏ liên kết, decided_by (chữ) giữ nguyên. Đơn nào còn đang chờ mà vẫn trỏ tới người này thì không
        // đặt trống: khóa ngoại chặn việc xóa và cả giao dịch quay lại.
        entityManager
            .createQuery(
                "select r from LeaveRequest r where r.approverEmployee.id = :id and r.employee.id <> :id and r.status not in :pending",
                LeaveRequest.class
            )
            .setParameter("id", id)
            .setParameter("pending", List.of(LeaveRequestStatus.CHO_DUYET, LeaveRequestStatus.CHO_HUY))
            .getResultList()
            .forEach(r -> r.setApproverEmployee(null));
        entityManager
            .createQuery(
                "select r from OvertimeRequest r where r.approverEmployee.id = :id and r.employee.id <> :id and r.status not in :pending",
                OvertimeRequest.class
            )
            .setParameter("id", id)
            .setParameter("pending", List.of(OvertimeStatus.CHO_DUYET, OvertimeStatus.CHO_XAC_NHAN_THEM))
            .getResultList()
            .forEach(r -> r.setApproverEmployee(null));
    }

    private void deleteFilesAfterCommit(List<String> files) {
        if (files.isEmpty()) {
            return;
        }
        List<String> paths = List.copyOf(new LinkedHashSet<>(files));
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String path : paths) {
                        try {
                            fileStorage.delete(path);
                        } catch (RuntimeException e) {
                            LOG.warn("Could not delete file {} of a permanently deleted employee: {}", path, e.getMessage());
                        }
                    }
                }
            }
        );
    }
}
