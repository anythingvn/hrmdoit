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
import vn.doit.hrm.service.numbering.NumberingSettings;
import vn.doit.hrm.service.numbering.NumberingSettingsService;

/**
 * Cấp số văn bản {@code {tiền tố}/{năm}/{mã loại}/{số}} (tiền tố và mã loại theo Cài đặt, mặc định DOIT và mã của {@link DocumentType}):
 * mỗi loại một dãy, chạy lại từ 1 mỗi năm. Đổi tiền tố/mã giữa năm thì dãy vẫn đếm tiếp, số đã cấp không bị sửa.
 * Dòng dãy số bị khóa trong lúc cấp nên không cấp trùng; số đã có trên văn bản khác (kể cả HR gõ tay) dưới tiền tố hiện hành thì bỏ qua.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;
    private final EmploymentContractRepository contractRepository;
    private final AssignmentDecisionRecordRepository decisionRepository;
    private final NumberingSettingsService settingsService;

    public DocumentNumberService(
        DocumentSequenceRepository sequenceRepository,
        EmploymentContractRepository contractRepository,
        AssignmentDecisionRecordRepository decisionRepository,
        NumberingSettingsService settingsService
    ) {
        this.sequenceRepository = sequenceRepository;
        this.contractRepository = contractRepository;
        this.decisionRepository = decisionRepository;
        this.settingsService = settingsService;
    }

    public static String format(NumberingSettings s, DocumentType type, int year, int n) {
        return s.documentPrefix() + "/" + year + "/" + s.documentCode(type) + "/" + n;
    }

    @Transactional
    public String next(DocumentType type, int year) {
        NumberingSettings settings = settingsService.current();
        DocumentSequence s = lock(type, year);
        int n = s.getNextValue();
        while (isTaken(format(settings, type, year, n))) {
            n++;
        }
        s.setNextValue(n + 1);
        s.setLastIssued(Math.max(s.getLastIssued(), n));
        return format(settings, type, year, n);
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
        NumberingSettings settings = settingsService.current();
        DocumentSequence s = lock(type, year);
        if (nextValue <= s.getLastIssued()) {
            throw new EmployeeRuleException(
                "Số bắt đầu phải lớn hơn số lớn nhất đã cấp trong năm (" + s.getLastIssued() + ")",
                "sequencebelowused"
            );
        }
        s.setNextValue(nextValue);
        return toDto(settings, s.getType(), year, s.getNextValue(), s.getLastIssued());
    }

    /** Mọi loại văn bản của năm, kể cả loại chưa cấp số nào. */
    @Transactional(readOnly = true)
    public List<DocumentSequenceDTO> list(int year) {
        NumberingSettings settings = settingsService.current();
        Map<DocumentType, DocumentSequence> rows = sequenceRepository
            .findByYear(year)
            .stream()
            .collect(Collectors.toMap(DocumentSequence::getType, Function.identity()));
        return Arrays.stream(DocumentType.values())
            .map(t -> {
                DocumentSequence s = rows.get(t);
                return s == null ? toDto(settings, t, year, 1, 0) : toDto(settings, t, year, s.getNextValue(), s.getLastIssued());
            })
            .toList();
    }

    private DocumentSequence lock(DocumentType type, int year) {
        sequenceRepository.insertIfMissing(type.name(), year);
        return sequenceRepository.findForUpdate(type, year).orElseThrow();
    }

    private static DocumentSequenceDTO toDto(NumberingSettings settings, DocumentType type, int year, int nextValue, int lastIssued) {
        return new DocumentSequenceDTO(
            type,
            settings.documentCode(type),
            year,
            nextValue,
            lastIssued,
            format(settings, type, year, nextValue)
        );
    }
}
