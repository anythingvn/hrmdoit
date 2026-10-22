package vn.doit.hrm.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.dto.ArchiveResultDTO;
import vn.doit.hrm.service.dto.ArchiveResultDTO.Ref;
import vn.doit.hrm.service.dto.ArchivedEmployeeDTO;

/**
 * Lưu trữ và khôi phục hồ sơ nhân viên. Chỉ đặt hoặc xóa {@code archived_at/by/note}; không xóa hay sửa dữ liệu nào khác
 * (trừ việc khóa tài khoản của hồ sơ tạo nhầm đang ở trạng thái Đang làm việc).
 */
@Service
@Transactional
public class EmployeeArchiveService {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeArchiveService.class);

    /** Mỗi truy vấn tìm một bản ghi gắn với hồ sơ (kiểm tra tồn tại); hồ sơ tạo nhầm là hồ sơ không có dòng nào trong các bảng này. */
    private static final List<String> CHILD_EXISTS = List.of(
        "select x.id from EmploymentContract x where x.employee.id = :id",
        "select x.id from SalaryChange x where x.employee.id = :id",
        "select x.id from Allowance x where x.employee.id = :id",
        "select x.id from AssignmentDecisionRecord x where x.employeeId = :id",
        "select x.id from EmployeeDocument x where x.employee.id = :id",
        "select x.id from LeaveRequest x where x.employee.id = :id",
        "select x.id from OvertimeRequest x where x.employee.id = :id",
        "select x.id from LeaveLedgerEntry x where x.employeeId = :id",
        "select x.id from PayrollLineRecord x where x.employee.id = :id",
        "select x.id from PayslipDelivery x where x.employee.id = :id",
        "select x.id from TerminationRecord x where x.employeeId = :id"
    );

    private final EmployeeRepository employeeRepository;

    private final DepartmentRepository departmentRepository;

    private final UserRepository userRepository;

    private final EntityManager entityManager;

    private final Clock clock;

    public EmployeeArchiveService(
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        UserRepository userRepository,
        EntityManager entityManager,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Đưa vào lưu trữ: hồ sơ Đã nghỉ hoặc hồ sơ tạo nhầm. Trả danh sách người và phòng còn nhận hồ sơ này làm quản lý/trưởng phòng.
     */
    public ArchiveResultDTO archive(Long id, String note) {
        LOG.debug("Request to archive Employee {}", id);
        Employee employee = lock(id);
        if (employee.isArchived()) {
            throw new EmployeeRuleException("Hồ sơ đã nằm trong vùng lưu trữ", "alreadyarchived");
        }
        boolean mistaken = false;
        if (employee.getStatus() != EmploymentStatus.DA_NGHI) {
            mistaken = isMistaken(id);
            if (!mistaken) {
                throw new EmployeeRuleException("Chỉ lưu trữ được nhân viên đã nghỉ việc. Hãy cho nghỉ việc trước", "archivenotterminated");
            }
        }
        String trimmed = note == null ? null : note.trim();
        if (trimmed != null && trimmed.length() > 500) {
            throw new EmployeeRuleException("Ghi chú tối đa 500 ký tự", "archivenotetoolong");
        }
        employee.setArchivedAt(Instant.now(clock));
        employee.setArchivedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        employee.setArchiveNote(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        if (mistaken) {
            User linked = employee.getUser();
            if (linked != null && linked.isActivated() && !IdNumbers.SYSTEM_ADMIN_LOGIN.equals(linked.getLogin())) {
                linked.setActivated(false);
                linked.markCredentialsChanged();
                userRepository.save(linked);
            }
        }
        employeeRepository.saveAndFlush(employee);
        List<Ref> managed = employeeRepository
            .findByManagerIdAndArchivedAtIsNullOrderByCode(id)
            .stream()
            .map(e -> new Ref(e.getId(), e.getCode(), e.getFullName()))
            .toList();
        List<Ref> headed = departmentRepository
            .findByHeadIdAndActiveTrueOrderByName(id)
            .stream()
            .map(d -> new Ref(d.getId(), null, d.getName()))
            .toList();
        return new ArchiveResultDTO(managed, headed);
    }

    /** Khôi phục: xóa trắng ba trường lưu trữ; trạng thái hồ sơ và tài khoản giữ nguyên. */
    public void restore(Long id) {
        LOG.debug("Request to restore Employee {}", id);
        Employee employee = lock(id);
        if (!employee.isArchived()) {
            throw new EmployeeRuleException("Hồ sơ chưa nằm trong vùng lưu trữ", "notarchived");
        }
        employee.setArchivedAt(null);
        employee.setArchivedBy(null);
        employee.setArchiveNote(null);
        employeeRepository.saveAndFlush(employee);
    }

    /** Vùng lưu trữ: tìm theo mã, họ tên, email (không dấu) hoặc CCCD; mới lưu trữ trước. */
    @Transactional(readOnly = true)
    public Page<ArchivedEmployeeDTO> archived(String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        String folded = VietnameseText.fold(query);
        String pattern = "%" + folded.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return employeeRepository
            .searchArchived(pattern, query, pageable)
            .map(e ->
                new ArchivedEmployeeDTO(
                    e.getId(),
                    e.getCode(),
                    e.getFullName(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(),
                    e.getLastWorkingDay(),
                    e.getArchivedAt(),
                    e.getArchivedBy(),
                    e.getArchiveNote()
                )
            );
    }

    /** Hồ sơ tạo nhầm: không có hợp đồng, thay đổi lương, phụ cấp, quyết định, tài liệu, đơn, sổ phép, dòng lương, phiếu lương hay nghỉ việc nào. */
    @Transactional(readOnly = true)
    public boolean isMistakenProfile(Long id) {
        return isMistaken(id);
    }

    private boolean isMistaken(Long id) {
        for (String jpql : CHILD_EXISTS) {
            boolean exists = !entityManager.createQuery(jpql).setParameter("id", id).setMaxResults(1).getResultList().isEmpty();
            if (exists) {
                return false;
            }
        }
        return true;
    }

    private Employee lock(Long id) {
        return employeeRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
    }
}
