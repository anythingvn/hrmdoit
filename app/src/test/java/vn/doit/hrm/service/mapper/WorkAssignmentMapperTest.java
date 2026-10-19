package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.WorkAssignmentAsserts.*;
import static vn.doit.hrm.domain.WorkAssignmentTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkAssignmentMapperTest {

    private WorkAssignmentMapper workAssignmentMapper;

    @BeforeEach
    void setUp() {
        workAssignmentMapper = new WorkAssignmentMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getWorkAssignmentSample1();
        var actual = workAssignmentMapper.toEntity(workAssignmentMapper.toDto(expected));
        assertWorkAssignmentAllPropertiesEquals(expected, actual);
    }
}
