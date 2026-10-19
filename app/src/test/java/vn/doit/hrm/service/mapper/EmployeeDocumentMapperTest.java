package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.EmployeeDocumentAsserts.*;
import static vn.doit.hrm.domain.EmployeeDocumentTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeeDocumentMapperTest {

    private EmployeeDocumentMapper employeeDocumentMapper;

    @BeforeEach
    void setUp() {
        employeeDocumentMapper = new EmployeeDocumentMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getEmployeeDocumentSample1();
        var actual = employeeDocumentMapper.toEntity(employeeDocumentMapper.toDto(expected));
        assertEmployeeDocumentAllPropertiesEquals(expected, actual);
    }
}
