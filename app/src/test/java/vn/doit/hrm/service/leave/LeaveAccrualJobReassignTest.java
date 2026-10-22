package vn.doit.hrm.service.leave;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.config.ApplicationProperties;
import vn.doit.hrm.repository.EmployeeRepository;

/** M4: việc 00:20 chuyển từng đơn trong transaction riêng; lỗi ở một đơn không chặn đơn khác, kể cả giữa đơn nghỉ và làm thêm. */
class LeaveAccrualJobReassignTest {

    @Test
    void oneFailingRequestDoesNotStopOthers() {
        LeaveRequestService requests = mock(LeaveRequestService.class);
        OvertimeService overtime = mock(OvertimeService.class);
        when(requests.orphanCandidates()).thenReturn(List.of(1L, 2L, 3L));
        when(requests.reassignOrphan(1L)).thenThrow(new IllegalStateException("hỏng"));
        when(requests.reassignOrphan(2L)).thenReturn(true);
        when(overtime.orphanCandidates()).thenThrow(new IllegalStateException("không đọc được"));
        LeaveAccrualJob job = new LeaveAccrualJob(
            Clock.systemUTC(),
            mock(EmployeeRepository.class),
            mock(LeaveAccrualService.class),
            new ApplicationProperties(),
            requests,
            overtime
        );

        job.reassignOrphans();

        verify(requests).reassignOrphan(1L);
        verify(requests).reassignOrphan(2L);
        verify(requests).reassignOrphan(3L);
        verify(overtime).orphanCandidates();
    }

    @Test
    void overtimeRequestsAreHandledOneByOne() {
        LeaveRequestService requests = mock(LeaveRequestService.class);
        OvertimeService overtime = mock(OvertimeService.class);
        when(requests.orphanCandidates()).thenReturn(List.of());
        when(overtime.orphanCandidates()).thenReturn(List.of(7L, 8L));
        when(overtime.reassignOrphan(7L)).thenThrow(new IllegalStateException("hỏng"));
        LeaveAccrualJob job = new LeaveAccrualJob(
            Clock.systemUTC(),
            mock(EmployeeRepository.class),
            mock(LeaveAccrualService.class),
            new ApplicationProperties(),
            requests,
            overtime
        );

        job.reassignOrphans();

        verify(overtime).reassignOrphan(7L);
        verify(overtime).reassignOrphan(8L);
    }
}
