package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.service.dto.WorkSiteDTO;

/**
 * Mapper for the entity {@link WorkSite} and its DTO {@link WorkSiteDTO}.
 */
@Mapper(componentModel = "spring")
public interface WorkSiteMapper extends EntityMapper<WorkSiteDTO, WorkSite> {}
