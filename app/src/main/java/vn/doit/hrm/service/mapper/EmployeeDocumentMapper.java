package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmployeeDocument;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.EmployeeDocumentDTO;

/**
 * Mapper for the entity {@link EmployeeDocument} and its DTO {@link EmployeeDocumentDTO}.
 */
@Mapper(componentModel = "spring")
public interface EmployeeDocumentMapper extends EntityMapper<EmployeeDocumentDTO, EmployeeDocument> {
    @Mapping(target = "employee", source = "employee", qualifiedByName = "employeeCode")
    EmployeeDocumentDTO toDto(EmployeeDocument s);

    @Named("employeeCode")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "code")
    EmployeeDTO toDtoEmployeeCode(Employee employee);
}
