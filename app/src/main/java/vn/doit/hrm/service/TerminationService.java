package vn.doit.hrm.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.TerminationRecordStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.LoginAttemptService;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.TerminationSnapshot.AssignmentEnd;
import vn.doit.hrm.service.TerminationSnapshot.ContractEnd;
import vn.doit.hrm.service.TerminationSnapshot.EmployeeState;
import vn.doit.hrm.service.dto.TerminationBlockerDTO;
import vn.doit.hrm.service.dto.TerminationRequestDTO;

/**
 * Cho nghỉ việc – cách duy nhất để chuyển trạng thái sang {@code DA_NGHI}.
 *
 * <ul>
 *   <li>Ngày làm việc cuối trước hôm nay: áp dụng ngay. Từ hôm nay trở đi (người đó còn làm hết ngày làm việc cuối): hẹn lịch
 *       ({@code SCHEDULED}), {@link TerminationScheduler} áp dụng sau nửa đêm của ngày làm việc cuối.</li>
 *   <li>Áp dụng: đặt trạng thái và các trường nghỉ việc, chốt vị trí đang mở và kết thúc HĐ đang hiệu lực về ngày làm việc cuối,
 *       khóa tài khoản gắn kèm. Không xóa bản ghi nào; mọi giá trị bị đổi được lưu vào snapshot để hoàn tác.</li>
 *   <li>Khóa theo thứ tự: dòng nhân viên trước, rồi dòng lịch nghỉ – mọi thao tác (cho nghỉ, hủy lịch, job, hoàn tác) đều vậy.</li>
 * </ul>
 */
@Service
@Transactional
public class TerminationService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminationService.class);

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SYSTEM = "system";

    private final EmployeeRepository employeeRepository;

    private final EmploymentContractRepository contractRepository;

    private final WorkAssignmentService workAssignmentService;

    private final WorkAssignmentRepository workAssignmentRepository;

    private final TerminationRecordRepository recordRepository;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    private final LoginAttemptService loginAttemptService;

    private final Clock clock;

    private final vn.doit.hrm.service.leave.LeaveTerminationHook leaveHook;

    private final vn.doit.hrm.service.ArchivedEmployeeGuard archivedGuard;

    private final vn.doit.hrm.repository.EmploymentPeriodRepository periodRepository;

    public TerminationService(
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository,
        WorkAssignmentService workAssignmentService,
        WorkAssignmentRepository workAssignmentRepository,
        TerminationRecordRepository recordRepository,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        LoginAttemptService loginAttemptService,
        Clock clock,
        vn.doit.hrm.service.leave.LeaveTerminationHook leaveHook,
        vn.doit.hrm.service.ArchivedEmployeeGuard archivedGuard,
        vn.doit.hrm.repository.EmploymentPeriodRepository periodRepository
    ) {
        this.periodRepository = periodRepository;
        this.archivedGuard = archivedGuard;
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.workAssignmentService = workAssignmentService;
        this.workAssignmentRepository = workAssignmentRepository;
        this.recordRepository = recordRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
        this.leaveHook = leaveHook;
    }

    /**
     * Cho nghỉ việc: áp dụng ngay nếu ngày làm việc cuối đã qua, ngược lại hẹn lịch.
     *
     * @return trạng thái của lần cho nghỉ ({@code APPLIED} hoặc {@code SCHEDULED})
     */
    public TerminationRecordStatus terminate(Long employeeId, TerminationRequestDTO request) {
        LOG.debug("Request to terminate Employee {} : {}", employeeId, request);
        Employee employee = lockEmployee(employeeId);
        archivedGuard.requireActive(employee);
        if (employee.getStatus() == EmploymentStatus.DA_NGHI) {
            throw new EmployeeRuleException("Nhân viên đã nghỉ việc", "alreadyterminated");
        }
        if (recordRepository.existsByEmployeeIdAndStatus(employeeId, TerminationRecordStatus.SCHEDULED)) {
            throw new EmployeeRuleException("Nhân viên đã có lịch nghỉ việc; hủy lịch trước khi đặt lại", "terminationscheduled");
        }
        if (request == null || request.lastWorkingDay() == null) {
            throw new EmployeeRuleException("Cần ngày làm việc cuối cùng", "lastworkingdayrequired");
        }
        if (request.terminationType() == null) {
            throw new EmployeeRuleException("Cần chọn hình thức nghỉ việc", "terminationtyperequired");
        }
        if (employee.getUser() != null && hasAdmin(employee.getUser()) && !currentUserIsAdmin()) {
            // Cho nghỉ sẽ khóa tài khoản Quản trị: chỉ Quản trị được làm (HR không được khóa/mở tài khoản Quản trị).
            throw new EmployeeRuleException(
                "Nhân viên này có tài khoản Quản trị — chỉ Quản trị được cho nghỉ việc",
                "adminaccountrequiresadmin"
            );
        }
        LocalDate lastDay = request.lastWorkingDay();
        LocalDate terminationDate = request.terminationDate() != null ? request.terminationDate() : lastDay.plusDays(1);
        if (terminationDate.isBefore(lastDay)) {
            throw new EmployeeRuleException("Ngày nghỉ việc không được trước ngày làm việc cuối", "terminationdatebeforelastday");
        }
        checkRules(employee, lastDay);

        TerminationRecord record = new TerminationRecord();
        record.setEmployeeId(employeeId);
        record.setLastWorkingDay(lastDay);
        record.setTerminationDate(terminationDate);
        record.setTerminationType(request.terminationType());
        record.setReason(request.terminationReason());
        record.setDeletedReturnPeriods(truncate(request.deletedReturnPeriods(), 500));
        record.setStatus(TerminationRecordStatus.SCHEDULED);
        record.setCreatedBy(currentLogin());
        record.setCreatedAt(Instant.now(clock));
        record = recordRepository.save(record);

        if (lastDay.isBefore(today())) {
            apply(record, employee);
        }
        recordRepository.flush();
        return record.getStatus();
    }

    /** Hủy lịch nghỉ việc chưa áp dụng. Không đổi gì khác. */
    public void cancel(Long employeeId) {
        LOG.debug("Request to cancel scheduled termination of Employee {}", employeeId);
        archivedGuard.requireActive(lockEmployee(employeeId));
        TerminationRecord record = recordRepository
            .findFirstByEmployeeIdAndStatusOrderByIdDesc(employeeId, TerminationRecordStatus.SCHEDULED)
            .orElseThrow(() -> new EmployeeRuleException("Nhân viên không có lịch nghỉ việc nào đang chờ", "noscheduledtermination"));
        record.setStatus(TerminationRecordStatus.CANCELLED);
        record.setCancelledBy(currentLogin());
        record.setCancelledAt(Instant.now(clock));
        recordRepository.saveAndFlush(record);
    }

    /** Id các lịch hẹn đến hạn (ngày làm việc cuối trước hôm nay). */
    @Transactional(readOnly = true)
    public List<Long> dueRecordIds() {
        return recordRepository.findDueIds(today());
    }

    /**
     * Áp dụng một lịch hẹn đã đến hạn (gọi từ job). An toàn khi chạy lặp hoặc trên nhiều máy: khóa dòng nhân viên và dòng lịch,
     * đọc lại trạng thái; chỉ lịch còn {@code SCHEDULED} mới được áp dụng.
     *
     * @return true nếu đã áp dụng; false nếu bỏ qua (đã xử lý, chưa đến hạn, hoặc vi phạm quy tắc – giữ lịch để HR xem lại)
     */
    public boolean applyScheduled(Long recordId) {
        Long employeeId = recordRepository.findEmployeeIdById(recordId).orElse(null);
        if (employeeId == null) {
            return false;
        }
        Employee employee = lockEmployee(employeeId);
        TerminationRecord record = recordRepository.findByIdForUpdate(recordId).orElse(null);
        if (record == null || record.getStatus() != TerminationRecordStatus.SCHEDULED || !record.getLastWorkingDay().isBefore(today())) {
            return false;
        }
        if (employee.getStatus() == EmploymentStatus.DA_NGHI) {
            LOG.warn("Bỏ qua lịch nghỉ việc {}: nhân viên {} đã nghỉ", recordId, employeeId);
            return false;
        }
        EmployeeRuleException violation = ruleViolation(employee, record.getLastWorkingDay());
        if (violation != null) {
            // Giữ lịch (nhãn "Quá hạn nghỉ việc" kèm lý do) để HR xử lý; job thử lại lần sau.
            LOG.warn("Chưa áp dụng được lịch nghỉ việc {} của nhân viên {}: {}", recordId, employeeId, violation.getMessage());
            String reason = violation.getMessage();
            record.setLastError(reason.length() > 500 ? reason.substring(0, 500) : reason);
            recordRepository.saveAndFlush(record);
            return false;
        }
        apply(record, employee);
        recordRepository.flush();
        return true;
    }

    /**
     * Hoàn tác lần cho nghỉ việc gần nhất đã áp dụng: trả lại đúng các giá trị việc cho nghỉ đã đổi và mở lại tài khoản.
     * Sửa của HR sau đó trên các trường khác được giữ nguyên. Nếu một giá trị do việc cho nghỉ đặt đã bị sửa tiếp → 400
     * {@code undoconflict} (liệt kê), không ghi đè – kể cả khi sau khi cho nghỉ có thêm giai đoạn làm việc sau ngày làm việc cuối
     * hoặc HĐ trùng thời gian với HĐ sẽ được mở lại.
     * Tài khoản chỉ được mở lại nếu hồ sơ vẫn gắn đúng tài khoản đó; tài khoản có {@code ROLE_ADMIN} chỉ được mở lại khi người
     * hoàn tác là Quản trị (HR hoàn tác thì tài khoản vẫn khóa, kèm ghi chú). Mở lại thì xóa bộ đếm đăng nhập sai của tài khoản và
     * JWT cũ vẫn hết hiệu lực (phải đăng nhập lại). Giai đoạn "quay về" đã xóa trước khi cho nghỉ không được khôi phục (chỉ báo lại).
     *
     * @return ghi chú cho người dùng (vd tài khoản không được mở lại), hoặc null
     */
    public String undo(Long employeeId) {
        LOG.debug("Request to undo termination of Employee {}", employeeId);
        Employee employee = lockEmployee(employeeId);
        archivedGuard.requireActive(employee);
        TerminationRecord latest = recordRepository
            .findFirstByEmployeeIdAndStatusOrderByIdDesc(employeeId, TerminationRecordStatus.APPLIED)
            .orElse(null);
        if (latest != null && periodRepository.existsByTerminationRecordId(latest.getId())) {
            // Lần cho nghỉ này đã khép một lần làm việc khi nhận làm lại: không trả hồ sơ về trạng thái trước lần nghỉ đó.
            throw new EmployeeRuleException(
                "Không hoàn tác được vì dữ liệu đã được sửa sau khi cho nghỉ: Nhân viên đã được nhận làm lại",
                "undoconflict"
            );
        }
        TerminationRecord record = java.util.Optional.ofNullable(latest)
            .filter(r -> employee.getStatus() == EmploymentStatus.DA_NGHI && r.getSnapshotJson() != null)
            .orElseThrow(() -> new EmployeeRuleException("Không có lần cho nghỉ việc nào để hoàn tác", "nothingtoundo"));
        TerminationSnapshot snapshot = objectMapper.readValue(record.getSnapshotJson(), TerminationSnapshot.class);

        List<String> conflicts = new ArrayList<>();
        EmployeeState after = snapshot.after();
        if (!stateOf(employee).equals(after)) {
            conflicts.add("Hồ sơ: trạng thái hoặc thông tin nghỉ việc đã đổi");
        }
        Map<Long, WorkAssignment> assignments = new HashMap<>();
        for (WorkAssignment w : workAssignmentRepository.findByEmployeeId(employeeId)) {
            assignments.put(w.getId(), w);
        }
        for (AssignmentEnd a : snapshot.assignments()) {
            WorkAssignment w = assignments.get(a.id());
            String label = "Giai đoạn làm việc từ " + vn(a.created() ? a.newFrom() : a.oldFrom());
            if (w == null) {
                conflicts.add(label + ": đã bị xóa");
            } else if (!Objects.equals(w.getFromDate(), a.newFrom()) || !Objects.equals(w.getToDate(), a.newTo())) {
                conflicts.add(label + ": ngày đã được sửa sau khi cho nghỉ");
            }
        }
        java.util.Set<Long> snapshotAssignmentIds = new java.util.HashSet<>();
        snapshot.assignments().forEach(a -> snapshotAssignmentIds.add(a.id()));
        for (WorkAssignment w : assignments.values()) {
            if (!snapshotAssignmentIds.contains(w.getId()) && after.lastWorkingDay() != null && w.getFromDate().isAfter(after.lastWorkingDay())) {
                conflicts.add("Giai đoạn làm việc từ " + vn(w.getFromDate()) + ": được thêm sau khi cho nghỉ, bắt đầu sau ngày làm việc cuối");
            }
        }
        Map<Long, EmploymentContract> contracts = new HashMap<>();
        for (EmploymentContract c : contractRepository.findByEmployeeId(employeeId)) {
            contracts.put(c.getId(), c);
        }
        java.util.Set<Long> snapshotContractIds = new java.util.HashSet<>();
        snapshot.contracts().forEach(c -> snapshotContractIds.add(c.id()));
        java.util.Set<Long> existedAtTermination = snapshot.contractIdsAtTermination() == null
            ? null
            : new java.util.HashSet<>(snapshot.contractIdsAtTermination());
        for (ContractEnd restored : snapshot.contracts()) {
            EmploymentContract entity = contracts.get(restored.id());
            if (entity == null || entity.getStartDate() == null) {
                continue;
            }
            for (EmploymentContract other : contracts.values()) {
                boolean signed = other.getStatus() == ContractStatus.DA_KY || other.getStatus() == ContractStatus.DA_KET_THUC;
                boolean added = existedAtTermination == null || !existedAtTermination.contains(other.getId());
                if (
                    !snapshotContractIds.contains(other.getId()) &&
                    signed &&
                    added &&
                    overlaps(entity.getStartDate(), restored.oldEndDate(), other.getStartDate(), other.getEndDate())
                ) {
                    conflicts.add(
                        "Hợp đồng " +
                        (other.getContractNo() == null ? "#" + other.getId() : other.getContractNo()) +
                        ": được thêm sau khi cho nghỉ, trùng thời gian với hợp đồng sẽ được mở lại"
                    );
                }
            }
        }
        for (ContractEnd c : snapshot.contracts()) {
            EmploymentContract entity = contracts.get(c.id());
            String label = "Hợp đồng " + (c.contractNo() == null ? "#" + c.id() : c.contractNo());
            if (entity == null) {
                conflicts.add(label + ": đã bị xóa");
            } else if (entity.getStatus() != c.newStatus() || !Objects.equals(entity.getEndDate(), c.newEndDate())) {
                conflicts.add(label + ": trạng thái hoặc ngày kết thúc đã được sửa sau khi cho nghỉ");
            }
        }
        if (!conflicts.isEmpty()) {
            throw new EmployeeRuleException(
                "Không hoàn tác được vì dữ liệu đã được sửa sau khi cho nghỉ: " + String.join("; ", conflicts),
                "undoconflict"
            );
        }

        EmployeeState before = snapshot.before();
        employee.setStatus(before.status());
        employee.setLastWorkingDay(before.lastWorkingDay());
        employee.setTerminationDate(before.terminationDate());
        employee.setTerminationType(before.terminationType());
        employee.setTerminationReason(before.terminationReason());
        employeeRepository.save(employee);
        for (AssignmentEnd a : snapshot.assignments()) {
            WorkAssignment w = assignments.get(a.id());
            if (a.created()) {
                workAssignmentRepository.delete(w);
            } else {
                w.setFromDate(a.oldFrom());
                w.setToDate(a.oldTo());
                workAssignmentRepository.save(w);
            }
        }
        for (ContractEnd c : snapshot.contracts()) {
            EmploymentContract entity = contracts.get(c.id());
            entity.setStatus(c.oldStatus());
            entity.setEndDate(c.oldEndDate());
            contractRepository.save(entity);
        }
        List<String> notices = new ArrayList<>();
        if (snapshot.userId() != null && Boolean.TRUE.equals(snapshot.userActivatedBefore())) {
            User linked = employee.getUser();
            if (linked != null && snapshot.userId().equals(linked.getId())) {
                if (!linked.isActivated()) {
                    if (hasAdmin(linked) && !currentUserIsAdmin()) {
                        // HR không được mở tài khoản Quản trị; để khóa, Quản trị mở lại ở trang Quản trị.
                        notices.add("Tài khoản quản trị: cần Quản trị mở lại (tài khoản " + linked.getLogin() + " vẫn đang khóa).");
                    } else {
                        linked.setActivated(true);
                        linked.markCredentialsChanged();
                        userRepository.save(linked);
                        // Các lần đăng nhập sai khi tài khoản bị khóa không chặn người dùng sau khi mở lại.
                        loginAttemptService.unlock(linked.getLogin());
                    }
                }
            } else {
                // Tài khoản đã bị bỏ gắn hoặc đổi sang tài khoản khác: không tự mở lại tài khoản cũ.
                notices.add("Tài khoản đăng nhập đã bị bỏ gắn hoặc đổi sau khi cho nghỉ — không mở lại tài khoản cũ.");
            }
        }
        if (record.getDeletedReturnPeriods() != null && !record.getDeletedReturnPeriods().isBlank()) {
            notices.add(
                "Giai đoạn “quay về” đã xóa khi cho nghỉ (" +
                record.getDeletedReturnPeriods() +
                ") không được khôi phục — thêm lại trong Lịch sử công tác nếu cần."
            );
        }
        String leaveNotice = leaveHook.onUndone(employee, record);
        if (leaveNotice != null) {
            notices.add(leaveNotice);
        }
        String notice = notices.isEmpty() ? null : String.join(" ", notices);
        record.setStatus(TerminationRecordStatus.UNDONE);
        record.setUndoneBy(currentLogin());
        record.setUndoneAt(Instant.now(clock));
        recordRepository.save(record);
        employeeRepository.flush();
        return notice;
    }

    // ------------------------------------------------------------------ nội bộ

    /** Hai khoảng ngày (đầu/cuối null = mở) có chung ít nhất một ngày. */
    private static boolean overlaps(LocalDate aStart, LocalDate aEnd, LocalDate bStart, LocalDate bEnd) {
        if (bStart == null) {
            return false;
        }
        return (aEnd == null || !bStart.isAfter(aEnd)) && (bEnd == null || !bEnd.isBefore(aStart));
    }

    private Employee lockEmployee(Long employeeId) {
        return employeeRepository
            .findByIdForUpdate(employeeId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
    }

    /** Quy tắc chặn; ném lỗi đầu tiên gặp. */
    private void checkRules(Employee employee, LocalDate lastDay) {
        EmployeeRuleException violation = ruleViolation(employee, lastDay);
        if (violation != null) {
            throw violation;
        }
    }

    /**
     * Quy tắc chặn (không ném, để job bỏ qua mà không làm hỏng transaction): HĐ đã ký bắt đầu sau ngày làm việc cuối;
     * giai đoạn làm việc bắt đầu sau ngày đó; tự cho mình nghỉ; khóa tài khoản ADMIN cuối cùng.
     */
    private EmployeeRuleException ruleViolation(Employee employee, LocalDate lastDay) {
        Long employeeId = employee.getId();
        EmployeeRuleException accountViolation = accountViolation(employee);
        if (accountViolation != null) {
            return accountViolation;
        }
        for (EmploymentContract c : contractRepository.findByEmployeeId(employeeId)) {
            if (c.getStatus() == ContractStatus.DA_KY && c.getStartDate() != null && c.getStartDate().isAfter(lastDay)) {
                return new EmployeeRuleException(
                    "Ngày làm việc cuối trước ngày bắt đầu hợp đồng " +
                    (c.getContractNo() == null ? "" : c.getContractNo() + " ") +
                    "(" +
                    c.getStartDate() +
                    ")",
                    "terminatebeforecontract"
                );
            }
        }
        List<TerminationBlockerDTO> blockers = workAssignmentService.blockers(employeeId, lastDay);
        if (!blockers.isEmpty()) {
            TerminationBlockerDTO b = blockers.get(0);
            return new EmployeeRuleException(
                "Ngày làm việc cuối trước giai đoạn làm việc " +
                (b.started() ? "đã bắt đầu" : "đã lên kế hoạch") +
                " từ " +
                vn(b.fromDate()) +
                "; kiểm tra lại ngày hoặc xóa giai đoạn đó",
                "terminatebeforeassignment"
            );
        }
        return null;
    }

    /**
     * Tài khoản gắn kèm: không được tự cho mình nghỉ (sẽ tự khóa tài khoản đang dùng); không được khóa tài khoản ADMIN đang hoạt động
     * cuối cùng (hệ thống mất quyền quản trị). Job chạy không có người đăng nhập nên chỉ kiểm tra ADMIN cuối cùng.
     */
    private EmployeeRuleException accountViolation(Employee employee) {
        User user = employee.getUser();
        if (user == null) {
            return null;
        }
        String current = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (current != null && current.equalsIgnoreCase(user.getLogin())) {
            return new EmployeeRuleException("Không thể tự cho mình nghỉ việc (tài khoản đang dùng sẽ bị khóa)", "selftermination");
        }
        if (hasAdmin(user) && user.isActivated() && userRepository.countActivatedWithAuthority(AuthoritiesConstants.ADMIN) <= 1) {
            return new EmployeeRuleException(
                "Tài khoản " + user.getLogin() + " là tài khoản quản trị cuối cùng đang hoạt động; tạo hoặc mở một tài khoản quản trị khác trước",
                "lastadmin"
            );
        }
        return null;
    }

    /** Áp dụng (đã kiểm tra quy tắc): đổi dữ liệu, ghi snapshot, khóa tài khoản. */
    private void apply(TerminationRecord record, Employee employee) {
        Long employeeId = employee.getId();
        LocalDate lastDay = record.getLastWorkingDay();
        EmployeeState before = stateOf(employee);
        Map<Long, LocalDate[]> assignmentsBefore = new HashMap<>();
        for (WorkAssignment w : workAssignmentRepository.findByEmployeeId(employeeId)) {
            assignmentsBefore.put(w.getId(), new LocalDate[] { w.getFromDate(), w.getToDate() });
        }

        employee.setStatus(EmploymentStatus.DA_NGHI);
        employee.setLastWorkingDay(lastDay);
        employee.setTerminationType(record.getTerminationType());
        employee.setTerminationReason(record.getReason());
        employee.setTerminationDate(record.getTerminationDate());
        employeeRepository.save(employee);

        workAssignmentService.closeAt(employeeId, lastDay);

        List<ContractEnd> contractEnds = new ArrayList<>();
        for (EmploymentContract c : contractRepository.findByEmployeeId(employeeId)) {
            if (c.getStatus() == ContractStatus.DA_KY && (c.getEndDate() == null || c.getEndDate().isAfter(lastDay))) {
                ContractStatus oldStatus = c.getStatus();
                LocalDate oldEnd = c.getEndDate();
                c.setStatus(ContractStatus.DA_KET_THUC);
                c.setEndDate(lastDay);
                contractRepository.save(c);
                contractEnds.add(new ContractEnd(c.getId(), c.getContractNo(), oldStatus, oldEnd, c.getStatus(), c.getEndDate()));
            }
        }

        List<AssignmentEnd> assignmentEnds = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (WorkAssignment w : workAssignmentRepository.findByEmployeeId(employeeId)) {
            seen.add(w.getId());
            LocalDate[] old = assignmentsBefore.get(w.getId());
            if (old == null) {
                assignmentEnds.add(new AssignmentEnd(w.getId(), null, null, w.getFromDate(), w.getToDate(), true));
            } else if (!Objects.equals(old[0], w.getFromDate()) || !Objects.equals(old[1], w.getToDate())) {
                assignmentEnds.add(new AssignmentEnd(w.getId(), old[0], old[1], w.getFromDate(), w.getToDate(), false));
            }
        }
        if (!seen.containsAll(assignmentsBefore.keySet())) {
            // Cho nghỉ việc không bao giờ được xóa lịch sử.
            throw new IllegalStateException("Cho nghỉ việc đã làm mất giai đoạn làm việc của nhân viên " + employeeId);
        }

        List<Long> contractIdsAtTermination = contractRepository.findByEmployeeId(employeeId).stream().map(EmploymentContract::getId).toList();
        Long userId = null;
        Boolean userActivatedBefore = null;
        User user = employee.getUser();
        if (user != null) {
            userId = user.getId();
            userActivatedBefore = user.isActivated();
            if (user.isActivated()) {
                user.setActivated(false);
                user.markCredentialsChanged();
                userRepository.save(user);
            }
        }

        TerminationSnapshot snapshot = new TerminationSnapshot(
            before,
            stateOf(employee),
            assignmentEnds,
            contractEnds,
            userId,
            userActivatedBefore,
            contractIdsAtTermination
        );
        record.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        record.setStatus(TerminationRecordStatus.APPLIED);
        record.setLastError(null);
        record.setAppliedAt(Instant.now(clock));
        record = recordRepository.save(record);
        // Nghỉ phép: hủy / rút ngắn đơn sau ngày làm việc cuối, chốt số dư, chuyển đơn người này đang duyệt.
        leaveHook.onApplied(employee, lastDay, record.getId());
        employeeRepository.flush();
        LOG.info("Đã áp dụng nghỉ việc cho nhân viên {} (ngày làm việc cuối {})", employeeId, lastDay);
    }

    private static boolean hasAdmin(User user) {
        return user.getAuthorities().stream().anyMatch(a -> AuthoritiesConstants.ADMIN.equals(a.getName()));
    }

    private static boolean currentUserIsAdmin() {
        return SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.ADMIN);
    }

    private static String truncate(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static EmployeeState stateOf(Employee e) {
        return new EmployeeState(e.getStatus(), e.getLastWorkingDay(), e.getTerminationDate(), e.getTerminationType(), e.getTerminationReason());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String currentLogin() {
        String login = SecurityUtils.getCurrentUserLogin().orElse(SYSTEM);
        return login.length() > 50 ? login.substring(0, 50) : login;
    }

    private static String vn(LocalDate d) {
        return d == null ? "?" : d.format(VN_DATE);
    }
}
