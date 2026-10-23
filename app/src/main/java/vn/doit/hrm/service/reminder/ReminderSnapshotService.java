package vn.doit.hrm.service.reminder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.reminder.EmployeeSnapshot;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.domain.enumeration.ContractStatus;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.repository.SalaryChangeRepository;

/** Lắp dữ liệu cho bộ nhắc từ hồ sơ: chỉ nhân viên (NHAN_VIEN) đang làm việc; người đã nghỉ không có lời nhắc. */
@Service
public class ReminderSnapshotService {

    private static final Set<ContractStatus> SIGNED = EnumSet.of(ContractStatus.DA_KY, ContractStatus.DA_KET_THUC);
    private static final Set<ContractType> LABOUR = EnumSet.of(ContractType.HDLD_XDTH, ContractType.HDLD_KXDTH);

    /** Quản lý trực tiếp: tên và email trên hồ sơ (email có thể trống). */
    public record Manager(Long id, String code, String name, String email) {}

    /** Liên kết từ lời nhắc về hồ sơ: hồ sơ, HĐTV gần nhất, HĐLĐ có thời hạn gần nhất, lần điều chỉnh lương gần nhất; quản lý. */
    public record Ref(Long employeeId, Long probationContractId, Long fixedContractId, Long salaryChangeId, Manager manager) {}

    /** Dữ liệu một lần chạy; {@code refs} theo mã nhân viên. */
    public record Loaded(List<EmployeeSnapshot> employees, Map<String, Ref> refs) {}

    private final EmployeeRepository employeeRepository;
    private final EmploymentContractRepository contractRepository;
    private final SalaryChangeRepository salaryChangeRepository;

    public ReminderSnapshotService(
        EmployeeRepository employeeRepository,
        EmploymentContractRepository contractRepository,
        SalaryChangeRepository salaryChangeRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.contractRepository = contractRepository;
        this.salaryChangeRepository = salaryChangeRepository;
    }

    @Transactional(readOnly = true)
    public Loaded load() {
        List<Employee> staff = employeeRepository.findByPersonTypeAndStatusAndArchivedAtIsNull(
            PersonType.NHAN_VIEN,
            EmploymentStatus.DANG_LAM_VIEC
        );
        List<Long> ids = staff.stream().map(Employee::getId).toList();
        Map<Long, List<EmploymentContract>> contracts = ids.isEmpty()
            ? Map.of()
            : contractRepository
                  .findByEmployeeIdIn(ids)
                  .stream()
                  .filter(c -> SIGNED.contains(c.getStatus()) && c.getStartDate() != null)
                  .collect(Collectors.groupingBy(c -> c.getEmployee().getId()));
        Map<Long, SalaryChange> lastRaise = ids.isEmpty()
            ? Map.of()
            : salaryChangeRepository
                  .findByEmployeeIdIn(ids)
                  .stream()
                  .filter(s -> s.getEffectiveDate() != null)
                  .collect(
                      Collectors.toMap(s -> s.getEmployee().getId(), Function.identity(), (a, b) ->
                          a.getEffectiveDate().isAfter(b.getEffectiveDate()) ? a : b
                      )
                  );

        List<EmployeeSnapshot> snapshots = new ArrayList<>();
        Map<String, Ref> refs = new LinkedHashMap<>();
        for (Employee e : staff) {
            List<EmploymentContract> own = contracts.getOrDefault(e.getId(), List.of());
            List<EmploymentContract> labour = own
                .stream()
                .filter(c -> LABOUR.contains(c.getType()))
                .toList();
            Optional<EmploymentContract> probation = latest(
                own
                    .stream()
                    .filter(c -> c.getType() == ContractType.HDTV)
                    .toList()
            );
            Optional<EmploymentContract> fixed = latest(labour).filter(c -> c.getEndDate() != null);
            SalaryChange raise = lastRaise.get(e.getId());
            snapshots.add(
                new EmployeeSnapshot(
                    e.getCode(),
                    e.getFullName(),
                    true,
                    e.getBirthDate(),
                    labour
                        .stream()
                        .map(c ->
                            new EmployeeSnapshot.Contract(
                                c.getContractNo() == null ? "" : c.getContractNo(),
                                c.getStartDate(),
                                c.getEndDate()
                            )
                        )
                        .toList(),
                    probation.map(EmploymentContract::getEndDate).orElse(null),
                    probation.map(c -> c.getProbationResult() != null).orElse(false),
                    raise != null ? raise.getEffectiveDate() : e.getJoinDate(),
                    raise != null ? raise.getReviewCycleMonths() : null,
                    raise != null ? raise.getNextReviewOverride() : null
                )
            );
            refs.put(
                e.getCode(),
                new Ref(
                    e.getId(),
                    probation.map(EmploymentContract::getId).orElse(null),
                    fixed.map(EmploymentContract::getId).orElse(null),
                    raise != null ? raise.getId() : null,
                    e.getManager() == null
                        ? null
                        : new Manager(
                              e.getManager().getId(),
                              e.getManager().getCode(),
                              e.getManager().getFullName(),
                              e.getManager().getEmail()
                          )
                )
            );
        }
        return new Loaded(snapshots, refs);
    }

    private static Optional<EmploymentContract> latest(List<EmploymentContract> contracts) {
        return contracts.stream().max(Comparator.comparing(EmploymentContract::getStartDate));
    }
}
