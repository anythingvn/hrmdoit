package vn.doit.hrm.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.doit.hr.payroll.ParsedPayroll;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hr.payroll.PayrollLine;
import vn.doit.hr.payroll.PayrollSheetParser;
import vn.doit.hr.payroll.Payslip;
import vn.doit.hr.payroll.PoiSheetGrid;

/** Phiếu lương dựng từ file mẫu ẩn danh: dòng "Thực lĩnh" bằng cột "Lương thực nhận" của từng người. */
class PayslipFixtureTest {

    @Test
    void netPayMatchesEveryLine() throws Exception {
        ParsedPayroll parsed;
        try (InputStream in = PayslipFixtureTest.class.getResourceAsStream(PayrollFixture.RESOURCE)) {
            parsed = new PayrollSheetParser().parse(PoiSheetGrid.read(in));
        }
        List<PayrollLine> lines = parsed.lines().stream().filter(l -> l.amounts().containsKey(PayrollField.THUC_NHAN)).toList();
        assertThat(lines).hasSize(14);
        int notProrated = 0;
        for (PayrollLine l : lines) {
            Payslip s = Payslip.of(l.amounts());
            assertThat(s.differenceFromNet(l.amounts()).abs()).as("dòng %d", l.excelRow()).isLessThanOrEqualTo(BigDecimal.ONE);
            if (!s.prorated()) {
                notProrated++;
            }
        }
        assertThat(notProrated).isEqualTo(3);
    }
}
