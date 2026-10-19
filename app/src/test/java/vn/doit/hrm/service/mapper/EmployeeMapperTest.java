package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.EmployeeAsserts.*;
import static vn.doit.hrm.domain.EmployeeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeeMapperTest {

    private EmployeeMapper employeeMapper;

    @BeforeEach
    void setUp() {
        employeeMapper = new EmployeeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getEmployeeSample1();
        // Trường nhạy cảm cố ý không có trong DTO trả về nên không đi vòng được.
        expected.setIdNumber(null);
        expected.setIdIssueDate(null);
        expected.setIdIssuePlace(null);
        expected.setTaxCode(null);
        expected.setBankAccount(null);
        expected.setBankName(null);
        expected.setSocialInsuranceNo(null);
        var actual = employeeMapper.toEntity(employeeMapper.toDto(expected));
        assertEmployeeAllPropertiesEquals(expected, actual);
    }
}
