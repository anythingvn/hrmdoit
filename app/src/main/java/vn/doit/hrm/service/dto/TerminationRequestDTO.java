package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.TerminationType;

/** Yêu cầu cho nghỉ việc. */
public record TerminationRequestDTO(LocalDate lastWorkingDay, TerminationType terminationType, String terminationReason) {}
