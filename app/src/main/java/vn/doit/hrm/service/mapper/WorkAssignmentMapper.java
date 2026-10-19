package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.WorkAssignment;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.WorkAssignmentDTO;
import vn.doit.hrm.service.dto.WorkSiteDTO;

/**
 * Mapper for the entity {@link WorkAssignment} and its DTO {@link WorkAssignmentDTO}.
 */
@Mapper(componentModel = "spring")
public interface WorkAssignmentMapper extends EntityMapper<WorkAssignmentDTO, WorkAssignment> {
    @Mapping(target = "employee", source = "employee", qualifiedByName = "employeeCode")
    @Mapping(target = "site", source = "site", qualifiedByName = "workSiteName")
    WorkAssignmentDTO toDto(WorkAssignment s);

    @Named("employeeCode")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "code")
    EmployeeDTO toDtoEmployeeCode(Employee employee);

    @Named("workSiteName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    WorkSiteDTO toDtoWorkSiteName(WorkSite workSite);
}
