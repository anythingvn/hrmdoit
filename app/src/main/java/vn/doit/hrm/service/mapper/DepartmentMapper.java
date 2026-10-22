package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.service.dto.DepartmentDTO;

/**
 * Mapper for the entity {@link Department} and its DTO {@link DepartmentDTO}. Trưởng phòng chỉ gán ở {@code DepartmentService}
 * (kiểm tra người đang làm).
 */
@Mapper(componentModel = "spring")
public interface DepartmentMapper extends EntityMapper<DepartmentDTO, Department> {
    @Override
    @Mapping(target = "headId", source = "head.id")
    @Mapping(target = "headName", source = "head.fullName")
    DepartmentDTO toDto(Department department);

    @Override
    @Mapping(target = "head", ignore = true)
    Department toEntity(DepartmentDTO dto);

    @Override
    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "head", ignore = true)
    void partialUpdate(@MappingTarget Department entity, DepartmentDTO dto);
}
