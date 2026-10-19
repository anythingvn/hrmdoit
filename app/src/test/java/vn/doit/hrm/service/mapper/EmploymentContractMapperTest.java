package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.EmploymentContractAsserts.*;
import static vn.doit.hrm.domain.EmploymentContractTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmploymentContractMapperTest {

    private EmploymentContractMapper employmentContractMapper;

    @BeforeEach
    void setUp() {
        employmentContractMapper = new EmploymentContractMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getEmploymentContractSample1();
        var actual = employmentContractMapper.toEntity(employmentContractMapper.toDto(expected));
        assertEmploymentContractAllPropertiesEquals(expected, actual);
    }
}
