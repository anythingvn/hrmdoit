package vn.doit.hrm.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.DocumentSequence;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.repository.AssignmentDecisionRecordRepository;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;
import vn.doit.hrm.service.dto.DocumentSequenceDTO;

/**
 * Cấp số văn bản {@code DOIT/{năm}/{mã loại}/{số}}: mỗi loại một dãy, chạy lại từ 1 mỗi năm.
 * Dòng dãy số bị khóa trong lúc cấp nên không cấp trùng; số HR đã gõ tay trên văn bản khác thì bỏ qua.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;
    private final EmploymentContractRepository contractRepository;
    private final AssignmentDecisionRecordRepository decisionRepository;

    public DocumentNumberService(
        DocumentSequenceRepository sequenceRepository,
        EmploymentContractRepository contractRepository,
        AssignmentDecisionRecordRepository decisionRepository
    ) {
        this.sequenceRepository = sequenceRepository;
        this.contractRepository = contractRepository;
        this.decisionRepository = decisionRepository;
    }

    public static String format(DocumentType type, int year, int n) {
        return "DOIT/" + year + "/" + type.code() + "/" + n;
    }

    @Transactional
    public String next(DocumentType type, int year) {
        DocumentSequence s = lock(type, year);
        int n = s.getNextValue();
        while (isTaken(format(type, year, n))) {
            n++;
        }
        s.setNextValue(n + 1);
        s.setLastIssued(Math.max(s.getLastIssued(), n));
        return format(type, year, n);
    }

    /** Số đã có trên một hợp đồng hoặc quyết định chưa. */
    @Transactional(readOnly = true)
    public boolean isTaken(String number) {
        return contractRepository.existsByContractNo(number) || decisionRepository.existsByDecisionNo(number);
    }

    /** Số đã có trên văn bản khác (trừ hợp đồng {@code contractId}, có thể null). */
    @Transactional(readOnly = true)
    public boolean isTakenByOther(String number, Long contractId) {
        if (contractId == null) {
            return isTaken(number);
        }
        return contractRepository.existsByContractNoAndIdNot(number, contractId) || decisionRepository.existsByDecisionNo(number);
    }

    @Transactional
    public DocumentSequenceDTO setNextValue(DocumentType type, int year, int nextValue) {
        if (nextValue < 1) {
            throw new EmployeeRuleException("Số bắt đầu phải từ 1 trở lên", "sequenceinvalid");
        }
        DocumentSequence s = lock(type, year);
        if (nextValue <= s.getLastIssued()) {
            throw new EmployeeRuleException(
                "Số bắt đầu phải lớn hơn số lớn nhất đã cấp trong năm (" + s.getLastIssued() + ")",
                "sequencebelowused"
            );
        }
        s.setNextValue(nextValue);
        return toDto(s.getType(), year, s.getNextValue(), s.getLastIssued());
    }

    /** Mọi loại văn bản của năm, kể cả loại chưa cấp số nào. */
    @Transactional(readOnly = true)
    public List<DocumentSequenceDTO> list(int year) {
        Map<DocumentType, DocumentSequence> rows = sequenceRepository
            .findByYear(year)
            .stream()
            .collect(Collectors.toMap(DocumentSequence::getType, Function.identity()));
        return Arrays.stream(DocumentType.values())
            .map(t -> {
                DocumentSequence s = rows.get(t);
                return s == null ? toDto(t, year, 1, 0) : toDto(t, year, s.getNextValue(), s.getLastIssued());
            })
            .toList();
    }

    private DocumentSequence lock(DocumentType type, int year) {
        sequenceRepository.insertIfMissing(type.name(), year);
        return sequenceRepository.findForUpdate(type, year).orElseThrow();
    }

    private static DocumentSequenceDTO toDto(DocumentType type, int year, int nextValue, int lastIssued) {
        return new DocumentSequenceDTO(type, type.code(), year, nextValue, lastIssued, format(type, year, nextValue));
    }
}
