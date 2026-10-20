package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.service.dto.DepartmentDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.EmploymentContractDTO;

/**
 * Mapper for the entity {@link EmploymentContract} and its DTO {@link EmploymentContractDTO}.
 */
@Mapper(componentModel = "spring")
public interface EmploymentContractMapper extends EntityMapper<EmploymentContractDTO, EmploymentContract> {
    @Mapping(target = "employee", source = "employee", qualifiedByName = "employeeCode")
    @Mapping(target = "department", source = "department", qualifiedByName = "departmentName")
    EmploymentContractDTO toDto(EmploymentContract s);

    @Named("employeeCode")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "code")
    EmployeeDTO toDtoEmployeeCode(Employee employee);

    @Named("departmentName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    DepartmentDTO toDtoDepartmentName(Department department);
}
