package vn.doit.hrm.service.numbering;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.CompanySetting;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.repository.CompanySettingRepository;
import vn.doit.hrm.service.CompanySettingService;
import vn.doit.hrm.service.dto.NumberingSettingsDTO;

/** Đọc / ghi cấu hình số văn bản và mã nhân viên trong {@code company_setting}; cột trống dùng mặc định. */
@Service
@Transactional
public class NumberingSettingsService {

    private final CompanySettingService companySettings;
    private final CompanySettingRepository repository;

    public NumberingSettingsService(CompanySettingService companySettings, CompanySettingRepository repository) {
        this.companySettings = companySettings;
        this.repository = repository;
    }

    /** Giá trị hiệu lực, luôn đọc lại từ DB (không cache). */
    @Transactional(readOnly = true)
    public NumberingSettings current() {
        NumberingSettingsDTO stored = stored(companySettings.current());
        NumberingSettings d = NumberingSettings.defaults();
        Map<DocumentType, String> codes = new EnumMap<>(DocumentType.class);
        for (DocumentType t : DocumentType.values()) {
            String c = stored.documentCodes().get(t);
            codes.put(t, c == null ? d.documentCode(t) : c);
        }
        return new NumberingSettings(
            or(stored.documentPrefix(), d.documentPrefix()),
            codes,
            or(stored.staffCodePrefix(), d.staffCodePrefix()),
            or(stored.internCodePrefix(), d.internCodePrefix()),
            or(stored.serviceCodePrefix(), d.serviceCodePrefix())
        );
    }

    /** Giá trị hiệu lực (đã thay mặc định) kèm giá trị mặc định để hiển thị gợi ý. */
    @Transactional(readOnly = true)
    public NumberingSettingsDTO view() {
        return withDefaults(current());
    }

    public NumberingSettingsDTO update(NumberingSettingsDTO in) {
        NumberingSettingsDTO n = NumberingRules.normalizeAndValidate(in);
        CompanySetting s = companySettings.current();
        s.setDocumentPrefix(n.documentPrefix());
        s.setDocumentCodeHdld(n.documentCodes().get(DocumentType.HDLD));
        s.setDocumentCodeHdtv(n.documentCodes().get(DocumentType.HDTV));
        s.setDocumentCodeHddt(n.documentCodes().get(DocumentType.HDDT));
        s.setDocumentCodeHddv(n.documentCodes().get(DocumentType.HDDV));
        s.setDocumentCodeQdcns(n.documentCodes().get(DocumentType.QD_CU_NHAN_SU));
        s.setStaffCodePrefix(n.staffCodePrefix());
        s.setInternCodePrefix(n.internCodePrefix());
        s.setServiceCodePrefix(n.serviceCodePrefix());
        repository.save(s);
        return view();
    }

    private static NumberingSettingsDTO withDefaults(NumberingSettings s) {
        return new NumberingSettingsDTO(
            s.documentPrefix(),
            new EnumMap<>(s.documentCodes()),
            s.staffCodePrefix(),
            s.internCodePrefix(),
            s.serviceCodePrefix(),
            NumberingRules.defaultsDto()
        );
    }

    private static NumberingSettingsDTO stored(CompanySetting s) {
        Map<DocumentType, String> codes = new EnumMap<>(DocumentType.class);
        put(codes, DocumentType.HDLD, s.getDocumentCodeHdld());
        put(codes, DocumentType.HDTV, s.getDocumentCodeHdtv());
        put(codes, DocumentType.HDDT, s.getDocumentCodeHddt());
        put(codes, DocumentType.HDDV, s.getDocumentCodeHddv());
        put(codes, DocumentType.QD_CU_NHAN_SU, s.getDocumentCodeQdcns());
        return new NumberingSettingsDTO(
            s.getDocumentPrefix(),
            codes,
            s.getStaffCodePrefix(),
            s.getInternCodePrefix(),
            s.getServiceCodePrefix(),
            null
        );
    }

    private static void put(Map<DocumentType, String> m, DocumentType t, String v) {
        if (v != null) {
            m.put(t, v);
        }
    }

    private static String or(String v, String d) {
        return v == null ? d : v;
    }
}
