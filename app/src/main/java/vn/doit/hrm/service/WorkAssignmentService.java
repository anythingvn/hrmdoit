package vn.doit.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentChange;
import vn.doit.hr.assignment.AssignmentConflictException;
import vn.doit.hr.assignment.AssignmentHistory;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.WorkAssignmentRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.service.dto.AssignmentSegmentDTO;
import vn.doit.hrm.service.dto.TerminationBlockerDTO;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.service.mapper.WorkAssignmentMapper;

/**
 * Lịch sử vị trí làm việc. Mọi thao tác ghi đi qua lõi {@link AssignmentHistory}; các thay đổi lõi trả về
 * ({@link AssignmentChange}) được lưu trong cùng một transaction: {@code after.id() == 0} → thêm bản ghi,
 * ngược lại → cập nhật bản ghi có id đó.
 */
@Service
@Transactional
public class WorkAssignmentService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkAssignmentService.class);

    private static final java.util.Comparator<WorkAssignment> BY_FROM = java.util.Comparator.comparing(
        WorkAssignment::getFromDate
    ).thenComparing(WorkAssignment::getId);

    private final WorkAssignmentRepository workAssignmentRepository;

    private final WorkAssignmentMapper workAssignmentMapper;

    private final EmployeeRepository employeeRepository;

    private final WorkSiteRepository workSiteRepository;

    private final Clock clock;

    private final PendingTerminationGuard pendingTerminationGuard;

    public WorkAssignmentService(
        WorkAssignmentRepository workAssignmentRepository,
        WorkAssignmentMapper workAssignmentMapper,
        EmployeeRepository employeeRepository,
        WorkSiteRepository workSiteRepository,
        Clock clock,
        PendingTerminationGuard pendingTerminationGuard
    ) {
        this.pendingTerminationGuard = pendingTerminationGuard;
        this.workAssignmentRepository = workAssignmentRepository;
        this.workAssignmentMapper = workAssignmentMapper;
        this.employeeRepository = employeeRepository;
        this.workSiteRepository = workSiteRepository;
        this.clock = clock;
    }

    /** Lịch sử đã nạp của một nhân viên: thực thể theo id và bản lõi tương ứng. */
    private record Loaded(Employee employee, Map<Long, WorkAssignment> byId, AssignmentHistory history) {}

    private Loaded load(Long employeeId) {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound"));
        List<WorkAssignment> rows = workAssignmentRepository.findByEmployeeId(employeeId);
        Map<Long, WorkAssignment> byId = rows.stream().collect(Collectors.toMap(WorkAssignment::getId, Function.identity()));
        AssignmentHistory history = AssignmentHistory.of(
            employee.getCode(),
            rows.stream().map(r -> AssignmentMapping.toCore(r, employee.getCode())).toList()
        );
        return new Loaded(employee, byId, history);
    }

    /**
     * Lưu các thay đổi lõi. Bản ghi mới (id 0) được thêm; bản CREATED nhận {@code siteForCreated},
     * bản RESUMED (quay về nơi cũ) kế thừa nơi làm việc của bản vừa bị chốt (CLOSED).
     *
     * @return thực thể đã lưu theo từng thay đổi, cùng thứ tự với {@code changes}
     */
    private List<WorkAssignment> persist(Loaded loaded, List<AssignmentChange> changes, WorkSite siteForCreated) {
        List<WorkAssignment> saved = new LinkedList<>();
        WorkAssignment lastClosed = null;
        for (AssignmentChange change : changes) {
            Assignment after = change.after();
            WorkAssignment entity;
            if (after.id() == 0) {
                entity = new WorkAssignment();
                entity.setEmployee(loaded.employee());
                if (change.kind() == AssignmentChange.Kind.CREATED) {
                    entity.setSite(siteForCreated);
                } else if (change.kind() == AssignmentChange.Kind.RESUMED && lastClosed != null) {
                    entity.setSite(lastClosed.getSite());
                }
            } else {
                entity = loaded.byId().get(after.id());
                if (entity == null) {
                    throw new IllegalStateException("Thay đổi tham chiếu bản ghi không tồn tại: " + after.id());
                }
            }
            AssignmentMapping.copyInto(after, entity);
            entity = workAssignmentRepository.save(entity);
            if (change.kind() == AssignmentChange.Kind.CLOSED) {
                lastClosed = entity;
            }
            LOG.debug("Lưu thay đổi vị trí làm việc: {}", change);
            saved.add(entity);
        }
        return saved;
    }

    /**
     * Thêm một giai đoạn làm việc. Nếu chọn {@code site} thì loại/đơn vị/địa chỉ lấy từ nơi làm việc đó.
     *
     * @return giai đoạn vừa thêm
     */
    public WorkAssignmentDTO add(Long employeeId, WorkAssignmentDTO dto) {
        LOG.debug("Request to add WorkAssignment for Employee {} : {}", employeeId, dto);
        Loaded loaded = load(employeeId);
        WorkSite site = null;
        var type = dto.getType();
        String unit = dto.getUnit();
        String address = dto.getAddress();
        if (dto.getSite() != null && dto.getSite().getId() != null) {
            site = workSiteRepository
                .findById(dto.getSite().getId())
                .orElseThrow(() -> new AssignmentConflictException("Không tìm thấy nơi làm việc đã chọn."));
            type = site.getType();
            unit = site.getName();
            address = site.getAddress();
        }
        Assignment requested = new Assignment(
            0,
            loaded.employee().getCode(),
            AssignmentMapping.toCore(type),
            unit,
            address,
            dto.getFromDate(),
            dto.getToDate(),
            dto.getDecisionNo(),
            dto.getNote()
        );
        AssignmentHistory.Result result = loaded.history().add(requested);
        // Có lịch nghỉ việc đang chờ: không tạo giai đoạn (kể cả giai đoạn "quay về" tự tạo) bắt đầu sau ngày làm việc cuối.
        for (AssignmentChange change : result.changes()) {
            pendingTerminationGuard.checkStart(employeeId, change.after().from());
        }
        List<WorkAssignment> saved = persist(loaded, result.changes(), site);
        int createdIndex = indexOf(result.changes(), AssignmentChange.Kind.CREATED);
        return workAssignmentMapper.toDto(saved.get(createdIndex));
    }

    /**
     * Đổi ngày kết thúc một giai đoạn (về sớm, gia hạn, chốt giai đoạn đang mở).
     *
     * @return giai đoạn sau khi đổi
     */
    public WorkAssignmentDTO changeEnd(Long employeeId, Long assignmentId, LocalDate newEnd) {
        LOG.debug("Request to change end of WorkAssignment {} of Employee {} to {}", assignmentId, employeeId, newEnd);
        Loaded loaded = load(employeeId);
        WorkAssignment entity = assignmentId == null ? null : loaded.byId().get(assignmentId);
        if (entity == null) {
            throw new AssignmentConflictException("Không tìm thấy giai đoạn làm việc " + assignmentId + " của nhân viên này.");
        }
        return workAssignmentMapper.toDto(changeEnd(loaded, entity, newEnd, true));
    }

    /**
     * Xóa một giai đoạn (sửa lịch sử nhập sai; Envers ghi lại bản đã xóa). Không tự nối các giai đoạn kề nhau:
     * nếu xóa giai đoạn chính ở giữa thì để lại khoảng trống (hiện ở {@link #gaps}).
     */
    public void delete(Long employeeId, Long assignmentId) {
        LOG.debug("Request to delete WorkAssignment {} of Employee {}", assignmentId, employeeId);
        Loaded loaded = load(employeeId);
        WorkAssignment entity = assignmentId == null ? null : loaded.byId().get(assignmentId);
        if (entity == null) {
            throw new EmployeeRuleException(
                "Không tìm thấy giai đoạn làm việc " + assignmentId + " của nhân viên này",
                "assignmentnotfound"
            );
        }
        workAssignmentRepository.delete(entity);
        workAssignmentRepository.flush();
    }

    /**
     * @param guardPending true khi HR đổi ngày: có lịch nghỉ việc đang chờ thì không được kéo dài giai đoạn qua ngày làm việc cuối
     *                     hoặc dời giai đoạn khác sang sau ngày đó (false khi chính việc cho nghỉ chốt lịch sử)
     */
    private WorkAssignment changeEnd(Loaded loaded, WorkAssignment entity, LocalDate newEnd, boolean guardPending) {
        Assignment target = AssignmentMapping.toCore(entity, loaded.employee().getCode());
        AssignmentHistory.Result result = loaded.history().changeEnd(target, newEnd);
        if (guardPending) {
            Long employeeId = loaded.employee().getId();
            pendingTerminationGuard.checkExtension(employeeId, entity.getToDate(), newEnd);
            for (AssignmentChange change : result.changes()) {
                pendingTerminationGuard.checkStart(employeeId, change.after().from());
            }
        }
        List<WorkAssignment> saved = persist(loaded, result.changes(), null);
        return saved.get(indexOf(result.changes(), AssignmentChange.Kind.END_CHANGED));
    }

    /**
     * Chốt lịch sử khi nhân viên nghỉ việc: giai đoạn còn mở hoặc kết thúc sau ngày làm việc cuối được chốt về ngày đó.
     * Không xóa giai đoạn nào (giai đoạn bắt đầu sau ngày đó bị {@link #checkCanCloseAt} chặn từ trước).
     */
    public void closeAt(Long employeeId, LocalDate lastWorkingDay) {
        checkCanCloseAt(employeeId, lastWorkingDay);
        while (true) {
            Loaded current = load(employeeId);
            Optional<WorkAssignment> open = current
                .byId()
                .values()
                .stream()
                .sorted(BY_FROM)
                .filter(e -> e.getToDate() == null || e.getToDate().isAfter(lastWorkingDay))
                .findFirst();
            if (open.isEmpty()) {
                return;
            }
            changeEnd(current, open.orElseThrow(), lastWorkingDay, false);
            workAssignmentRepository.flush();
        }
    }

    /**
     * Chốt chặn cuối (TerminationService đã kiểm tra và báo lỗi cho người dùng): không được nghỉ việc trước một giai đoạn bắt đầu sau ngày làm việc cuối – dù giai đoạn đó đã
     * bắt đầu (lịch sử thật, thường do gõ nhầm ngày) hay chưa (kế hoạch, vd "quay về văn phòng" tự tạo). Cho nghỉ việc
     * không xóa dữ liệu; HR tự xóa giai đoạn đó (có nhật ký) nếu đúng là không còn cần.
     */
    @Transactional(readOnly = true)
    public void checkCanCloseAt(Long employeeId, LocalDate lastWorkingDay) {
        if (!blockers(employeeId, lastWorkingDay).isEmpty()) {
            throw new IllegalStateException("Còn giai đoạn làm việc bắt đầu sau ngày làm việc cuối " + lastWorkingDay);
        }
    }

    /**
     * Các giai đoạn bắt đầu sau ngày làm việc cuối (chặn việc cho nghỉ). {@code autoReturn}: giai đoạn "quay về nơi cũ" do hệ
     * thống tự tạo khi thêm một giai đoạn có ngày kết thúc (cùng nơi, cùng thông tin với giai đoạn ngay trước giai đoạn đó),
     * chưa bắt đầu – giao diện cho phép xóa nó rồi cho nghỉ trong một lần xác nhận.
     */
    @Transactional(readOnly = true)
    public List<TerminationBlockerDTO> blockers(Long employeeId, LocalDate lastWorkingDay) {
        LocalDate today = LocalDate.now(clock);
        List<WorkAssignment> all = load(employeeId).byId().values().stream().sorted(BY_FROM).toList();
        return all
            .stream()
            .filter(e -> e.getFromDate().isAfter(lastWorkingDay))
            .map(e ->
                new TerminationBlockerDTO(
                    e.getId(),
                    e.getType(),
                    e.getUnit(),
                    e.getFromDate(),
                    e.getToDate(),
                    !e.getFromDate().isAfter(today),
                    e.getFromDate().isAfter(today) && isAutoReturn(e, all)
                )
            )
            .toList();
    }

    private static boolean isPrimary(WorkAssignment e) {
        return e.getType() != null && e.getType() != vn.doit.hrm.domain.enumeration.WorkLocationType.CONG_TAC;
    }

    /** Dấu hiệu của bản RESUMED do lõi tạo: p (trước) – a (chen giữa, có ngày kết thúc) – e (= p dời ngày bắt đầu). */
    private static boolean isAutoReturn(WorkAssignment e, List<WorkAssignment> all) {
        if (!isPrimary(e)) {
            return false;
        }
        LocalDate dayBefore = e.getFromDate().minusDays(1);
        for (WorkAssignment a : all) {
            if (a == e || !isPrimary(a) || !dayBefore.equals(a.getToDate())) {
                continue;
            }
            LocalDate beforeA = a.getFromDate().minusDays(1);
            for (WorkAssignment p : all) {
                if (p != e && p != a && isPrimary(p) && beforeA.equals(p.getToDate()) && samePlace(p, e)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean samePlace(WorkAssignment p, WorkAssignment e) {
        return (
            p.getType() == e.getType() &&
            java.util.Objects.equals(p.getUnit(), e.getUnit()) &&
            java.util.Objects.equals(p.getAddress(), e.getAddress()) &&
            java.util.Objects.equals(p.getDecisionNo(), e.getDecisionNo()) &&
            java.util.Objects.equals(p.getNote(), e.getNote()) &&
            java.util.Objects.equals(
                p.getSite() == null ? null : p.getSite().getId(),
                e.getSite() == null ? null : e.getSite().getId()
            )
        );
    }

    /**
     * Dòng thời gian thực tế. Thiếu {@code from}: từ giai đoạn sớm nhất. Thiếu {@code to}: đến hôm nay hoặc mốc
     * cuối cùng đã biết (lấy mốc muộn hơn); khi đó đoạn cuối đang mở được trả {@code to = null}.
     */
    @Transactional(readOnly = true)
    public List<AssignmentSegmentDTO> timeline(Long employeeId, LocalDate from, LocalDate to) {
        AssignmentHistory history = load(employeeId).history();
        List<Assignment> entries = history.entries();
        if (entries.isEmpty() && (from == null || to == null)) {
            return List.of();
        }
        LocalDate start = from != null ? from : entries.get(0).from();
        boolean openEnd = to == null;
        LocalDate end = to;
        if (end == null) {
            end = LocalDate.now(clock);
            for (Assignment a : entries) {
                LocalDate mark = a.to() != null ? a.to() : a.from();
                if (mark.isAfter(end)) {
                    end = mark;
                }
            }
        }
        checkRange(start, end);
        List<AssignmentHistory.Segment> segments = history.timeline(start, end);
        List<AssignmentSegmentDTO> out = new LinkedList<>();
        for (int i = 0; i < segments.size(); i++) {
            AssignmentHistory.Segment s = segments.get(i);
            boolean last = i == segments.size() - 1;
            LocalDate segTo = openEnd && last && s.assignment() != null && s.assignment().openEnded() ? null : s.to();
            out.add(toSegment(s, segTo));
        }
        return out;
    }

    /** Các khoảng không có nơi làm việc chính nào trong [from, to]. */
    @Transactional(readOnly = true)
    public List<AssignmentSegmentDTO> gaps(Long employeeId, LocalDate from, LocalDate to) {
        checkRange(from, to);
        return load(employeeId).history().gaps(from, to).stream().map(s -> toSegment(s, s.to())).toList();
    }

    /** Nơi làm việc thực tế hôm nay (đang công tác thì là nơi công tác). */
    @Transactional(readOnly = true)
    public Optional<WorkAssignmentDTO> current(Long employeeId) {
        Loaded loaded = load(employeeId);
        return loaded.history().locationOn(LocalDate.now(clock)).map(a -> loaded.byId().get(a.id())).map(workAssignmentMapper::toDto);
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new AssignmentConflictException("Khoảng thời gian không hợp lệ.");
        }
    }

    private static AssignmentSegmentDTO toSegment(AssignmentHistory.Segment s, LocalDate to) {
        Assignment a = s.assignment();
        return new AssignmentSegmentDTO(
            a == null || a.id() == 0 ? null : a.id(),
            s.from(),
            to,
            a == null ? null : AssignmentMapping.toApp(a.type()),
            a == null ? null : a.unit(),
            s.label()
        );
    }

    private static int indexOf(List<AssignmentChange> changes, AssignmentChange.Kind kind) {
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i).kind() == kind) {
                return i;
            }
        }
        throw new IllegalStateException("Thiếu thay đổi " + kind);
    }

    /**
     * Get all the workAssignments.
     *
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public List<WorkAssignmentDTO> findAll() {
        LOG.debug("Request to get all WorkAssignments");
        return workAssignmentRepository
            .findAll()
            .stream()
            .map(workAssignmentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get all the workAssignments with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public List<WorkAssignmentDTO> findAllWithEagerRelationships() {
        return workAssignmentRepository
            .findAllWithEagerRelationships()
            .stream()
            .map(workAssignmentMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    /**
     * Get one workAssignment by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<WorkAssignmentDTO> findOne(Long id) {
        LOG.debug("Request to get WorkAssignment : {}", id);
        return workAssignmentRepository.findOneWithEagerRelationships(id).map(workAssignmentMapper::toDto);
    }
}
