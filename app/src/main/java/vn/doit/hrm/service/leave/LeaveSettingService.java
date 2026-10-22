package vn.doit.hrm.service.leave;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.LeaveSetting;
import vn.doit.hrm.repository.LeaveSettingRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveSettingDTO;

/** Cài đặt nghỉ phép (một dòng, id = 1): mọi vai trò đọc, chỉ Quản trị sửa (kiểm tra ở API). */
@Service
@Transactional
public class LeaveSettingService {

    private final LeaveSettingRepository repository;
    private final Clock clock;

    public LeaveSettingService(LeaveSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LeaveSetting current() {
        return repository.findById(1L).orElseThrow(() -> new IllegalStateException("Thiếu dòng leave_setting id = 1"));
    }

    @Transactional(readOnly = true)
    public LeaveSettingDTO get() {
        return toDto(current());
    }

    public LeaveSettingDTO update(LeaveSettingDTO dto) {
        try {
            MonthDay.of(dto.carryExpiryMonth(), dto.carryExpiryDay());
        } catch (DateTimeException e) {
            throw new EmployeeRuleException("Hạn dùng phép năm trước không phải ngày hợp lệ", "leavesettinginvalid");
        }
        if (dto.carryExpiryMonth() == 2 && dto.carryExpiryDay() == 29) {
            throw new EmployeeRuleException("Hạn dùng phép năm trước không được là 29/02", "leavesettinginvalid");
        }
        if (dto.otYearCap() < dto.otMonthCap()) {
            throw new EmployeeRuleException("Giới hạn làm thêm năm phải không nhỏ hơn giới hạn tháng", "leavesettinginvalid");
        }
        LeaveSetting s = current();
        boolean expiryChanged =
            s.getCarryExpiryMonth() != dto.carryExpiryMonth() || s.getCarryExpiryDay() != dto.carryExpiryDay();
        LocalDate today = LocalDate.now(clock);
        // Không đổi hạn về một ngày đã qua của năm nay: phần đã trừ sau ngày đó không được tính lại là hết hạn (không áp dụng ngược).
        if (expiryChanged && LocalDate.of(today.getYear(), dto.carryExpiryMonth(), dto.carryExpiryDay()).isBefore(today)) {
            throw new EmployeeRuleException("Ngày hết hạn phép năm trước không được trước hôm nay", "leaveexpiryinpast");
        }
        s.setAdvanceDays(dto.advanceDays());
        s.setDirectorThresholdDays(dto.directorThresholdDays());
        s.setOtMonthCap(dto.otMonthCap());
        s.setOtYearCap(dto.otYearCap());
        s.setCarryExpiryMonth(dto.carryExpiryMonth());
        s.setCarryExpiryDay(dto.carryExpiryDay());
        s.setPastRequestDays(dto.pastRequestDays());
        s.setOtConfirmDay(dto.otConfirmDay());
        return toDto(repository.save(s));
    }

    /** Ngày cuối cùng còn dùng được phép chuyển từ năm trước sang năm {@code year} (mặc định 31/03). */
    @Transactional(readOnly = true)
    public LocalDate carryExpiry(int year) {
        LeaveSetting s = current();
        return LocalDate.of(year, s.getCarryExpiryMonth(), s.getCarryExpiryDay());
    }

    private static LeaveSettingDTO toDto(LeaveSetting s) {
        return new LeaveSettingDTO(
            s.getAdvanceDays(),
            s.getDirectorThresholdDays(),
            s.getOtMonthCap(),
            s.getOtYearCap(),
            s.getCarryExpiryMonth(),
            s.getCarryExpiryDay(),
            s.getPastRequestDays(),
            s.getOtConfirmDay()
        );
    }
}
