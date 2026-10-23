package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.doit.hr.contract.ContractData;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.ContractType;
import vn.doit.hrm.domain.enumeration.Gender;
import vn.doit.hrm.domain.enumeration.SalaryBasis;

class ContractDataFactoryTest {

    private final ContractDataFactory factory = new ContractDataFactory(company());

    private static CompanySettingService company() {
        return representative("Trần Ngọc Nga");
    }

    private static CompanySettingService representative(String name) {
        CompanySettingService s = mock(CompanySettingService.class);
        when(s.representativeName()).thenReturn(name);
        return s;
    }

    private static Employee employee() {
        return new Employee()
            .fullName("Nguyễn Thị Hồng Nhung")
            .gender(Gender.NU)
            .birthDate(LocalDate.of(1996, 4, 2))
            .residence("Phường Cầu Giấy, Hà Nội")
            .phone("0900000000")
            .education("Đại học")
            .idNumber("001196000000")
            .idIssueDate(LocalDate.of(2021, 5, 10))
            .idIssuePlace("Cục Cảnh sát QLHC về TTXH")
            .bankAccount("0123456789")
            .bankName("Vietcombank")
            .position("Kiểm thử")
            .department(new Department().name("Phòng Kiểm thử"));
    }

    private static EmploymentContract netContract() {
        return new EmploymentContract()
            .type(ContractType.HDLD_XDTH)
            .termMonths(24)
            .startDate(LocalDate.of(2026, 10, 25))
            .endDate(LocalDate.of(2028, 10, 24))
            .position("Lập trình viên")
            .salary(26_215_000L)
            .salaryBasis(SalaryBasis.NET)
            .grossAmount(30_500_000L)
            .raisePolicy("Xét nâng lương mỗi năm một lần")
            .department(new Department().name("Phòng Phát triển phần mềm"));
    }

    @Test
    void netSalaryPrintsAgreedNetAmountOnly() {
        ContractData d = factory.build(employee(), netContract(), List.of());
        Map<String, String> m = factory.fields(d, netContract());
        assertThat(m.get("mucLuong")).isEqualTo("26.215.000");
        assertThat(m.get("mucLuongBangChu")).containsIgnoringCase("hai mươi sáu triệu");
        assertThat(m.values()).noneMatch(v -> v.contains("30.500.000"));
        assertThat(m.get("hoTen")).isEqualTo("NGUYỄN THỊ HỒNG NHUNG");
        assertThat(m.get("gioiTinh")).isEqualTo("Nữ");
        assertThat(m.get("viTri")).isEqualTo("Lập trình viên");
        assertThat(m.get("boPhan")).isEqualTo("Phòng Phát triển phần mềm");
        assertThat(m.get("nguoiDaiDien")).isEqualTo("TRẦN NGỌC NGA");
    }

    @Test
    void endDateIsStartPlusTermMinusOneDay() {
        Map<String, String> m = factory.fields(factory.build(employee(), netContract(), List.of()), netContract());
        assertThat(m.get("tuNgay")).isEqualTo("25/10/2026");
        assertThat(m.get("denNgay")).isEqualTo("24/10/2028");
        assertThat(m.get("thoiHan")).isEqualTo("02 năm");
    }

    @Test
    void handEditedEndDateIsPrinted() {
        EmploymentContract c = netContract().endDate(LocalDate.of(2028, 10, 31));
        assertThat(factory.fields(factory.build(employee(), c, List.of()), c).get("denNgay")).isEqualTo("31/10/2028");
    }

    @Test
    void probationWithoutTermPrintsItsDates() {
        EmploymentContract c = new EmploymentContract()
            .type(ContractType.HDTV)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2026, 12, 31))
            .salary(10_000_000L);
        Map<String, String> m = factory.fields(factory.build(employee(), c, List.of()), c);
        assertThat(m.get("loaiHopDong")).isEqualTo("Xác định thời hạn");
        assertThat(m.get("thoiHan")).isEqualTo("02 tháng");
        assertThat(m.get("denNgay")).isEqualTo("31/12/2026");
    }

    @Test
    void indefiniteContract() {
        EmploymentContract c = netContract().type(ContractType.HDLD_KXDTH).termMonths(null).endDate(null);
        Map<String, String> m = factory.fields(factory.build(employee(), c, List.of()), c);
        assertThat(m.get("loaiHopDong")).isEqualTo("Không xác định thời hạn");
        assertThat(m.get("denNgay")).isEqualTo("khi chấm dứt theo quy định");
    }

    @Test
    void onlyAllowancesEffectiveOnStartDateArePrinted() {
        List<Allowance> allowances = List.of(
            new Allowance().name("Phụ cấp cũ").amountPerMonth(500_000L).fromDate(LocalDate.of(2025, 1, 1)).toDate(LocalDate.of(2026, 10, 24)),
            new Allowance().name("Phụ cấp ăn trưa").amountPerMonth(730_000L).fromDate(LocalDate.of(2026, 1, 1)),
            new Allowance().name("Phụ cấp sau này").amountPerMonth(1_000_000L).fromDate(LocalDate.of(2027, 1, 1))
        );
        ContractData d = factory.build(employee(), netContract(), allowances);
        assertThat(d.allowances()).extracting(ContractData.Allowance::name).containsExactly("Phụ cấp ăn trưa");
        assertThat(factory.fields(d, netContract()).get("phuCap")).isEqualTo("Phụ cấp ăn trưa 730.000 đồng/tháng");
    }

    @Test
    void departmentAndPositionFallBackToProfile() {
        EmploymentContract c = netContract().department(null).position(null);
        ContractData d = factory.build(employee(), c, List.of());
        assertThat(d.department()).isEqualTo("Phòng Kiểm thử");
        assertThat(d.position()).isEqualTo("Kiểm thử");
    }

    @Test
    void missingFieldsNamedInVietnamese() {
        Employee e = employee().idNumber(null).residence("  ");
        assertThat(factory.missingFields(factory.build(e, netContract(), List.of()))).containsExactly("Số CCCD", "Nơi cư trú");
        assertThat(factory.missingFields(factory.build(employee(), netContract(), List.of()))).isEmpty();
    }

    @Test
    void missingSalaryAndRepresentative() {
        ContractDataFactory noRep = new ContractDataFactory(representative(""));
        EmploymentContract c = netContract().salary(null);
        assertThat(noRep.missingFields(noRep.build(employee(), c, List.of()))).containsExactly("Mức lương", "Người đại diện công ty");
    }
}
