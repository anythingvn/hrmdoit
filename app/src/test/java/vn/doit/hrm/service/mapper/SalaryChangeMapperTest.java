package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.SalaryChangeAsserts.*;
import static vn.doit.hrm.domain.SalaryChangeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SalaryChangeMapperTest {

    private SalaryChangeMapper salaryChangeMapper;

    @BeforeEach
    void setUp() {
        salaryChangeMapper = new SalaryChangeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getSalaryChangeSample1();
        var actual = salaryChangeMapper.toEntity(salaryChangeMapper.toDto(expected));
        assertSalaryChangeAllPropertiesEquals(expected, actual);
    }
}
