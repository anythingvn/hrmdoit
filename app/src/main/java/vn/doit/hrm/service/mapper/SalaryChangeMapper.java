package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.SalaryChange;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.SalaryChangeDTO;

/**
 * Mapper for the entity {@link SalaryChange} and its DTO {@link SalaryChangeDTO}.
 */
@Mapper(componentModel = "spring")
public interface SalaryChangeMapper extends EntityMapper<SalaryChangeDTO, SalaryChange> {
    @Mapping(target = "employee", source = "employee", qualifiedByName = "employeeCode")
    SalaryChangeDTO toDto(SalaryChange s);

    @Named("employeeCode")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "code")
    EmployeeDTO toDtoEmployeeCode(Employee employee);
}
