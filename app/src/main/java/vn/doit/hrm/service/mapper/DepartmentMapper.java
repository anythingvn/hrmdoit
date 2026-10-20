package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.service.dto.DepartmentDTO;

/**
 * Mapper for the entity {@link Department} and its DTO {@link DepartmentDTO}.
 */
@Mapper(componentModel = "spring")
public interface DepartmentMapper extends EntityMapper<DepartmentDTO, Department> {}
