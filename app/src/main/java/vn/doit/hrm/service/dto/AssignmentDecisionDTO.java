package vn.doit.hrm.service.dto;

import java.time.LocalDate;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/** Quyết định cử nhân sự đã ban hành (nơi đến, thời gian như trên văn bản). */
public record AssignmentDecisionDTO(
    Long id,
    String decisionNo,
    Long assignmentId,
    WorkLocationType type,
    String unit,
    String address,
    LocalDate fromDate,
    LocalDate toDate,
    LocalDate signDate,
    String signerName,
    String signerTitle,
    String task,
    String benefits
) {}
