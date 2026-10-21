package vn.doit.hrm.service.dto;

import vn.doit.hrm.domain.enumeration.DocumentType;

/** Một dãy số văn bản trong năm; {@code nextNumber} là số sẽ cấp tiếp (chưa tính số HR đã gõ tay). */
public record DocumentSequenceDTO(DocumentType type, String code, int year, int nextValue, int lastIssued, String nextNumber) {}
