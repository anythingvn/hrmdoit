package vn.doit.hrm.service.mapper;

import static vn.doit.hrm.domain.WorkSiteAsserts.*;
import static vn.doit.hrm.domain.WorkSiteTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkSiteMapperTest {

    private WorkSiteMapper workSiteMapper;

    @BeforeEach
    void setUp() {
        workSiteMapper = new WorkSiteMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getWorkSiteSample1();
        var actual = workSiteMapper.toEntity(workSiteMapper.toDto(expected));
        assertWorkSiteAllPropertiesEquals(expected, actual);
    }
}
