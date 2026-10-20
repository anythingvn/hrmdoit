package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.AllowanceAsserts.*;
import static vn.doit.hrm.domain.AllowanceTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AllowanceMapperTest {

    private AllowanceMapper allowanceMapper;

    @BeforeEach
    void setUp() {
        allowanceMapper = new AllowanceMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAllowanceSample1();
        var actual = allowanceMapper.toEntity(allowanceMapper.toDto(expected));
        assertAllowanceAllPropertiesEquals(expected, actual);
    }
}
