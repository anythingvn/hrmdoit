package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Allowance;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.service.dto.AllowanceDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;

/**
 * Mapper for the entity {@link Allowance} and its DTO {@link AllowanceDTO}.
 */
@Mapper(componentModel = "spring")
public interface AllowanceMapper extends EntityMapper<AllowanceDTO, Allowance> {
    @Mapping(target = "employee", source = "employee", qualifiedByName = "employeeCode")
    AllowanceDTO toDto(Allowance s);

    @Named("employeeCode")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "code")
    EmployeeDTO toDtoEmployeeCode(Employee employee);
}
