package vn.doit.hrm.service.numbering;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.NumberingSettingsDTO;

/** Chuẩn hóa và kiểm tra cấu hình số văn bản, mã nhân viên. */
public final class NumberingRules {

    private static final Pattern DOC = Pattern.compile("^[\\p{Lu}0-9-]{1,10}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9]{0,9}$");

    private NumberingRules() {}

    /** Giá trị mặc định dạng DTO (không lồng {@code defaults}). */
    public static NumberingSettingsDTO defaultsDto() {
        NumberingSettings d = NumberingSettings.defaults();
        return new NumberingSettingsDTO(
            d.documentPrefix(),
            new EnumMap<>(d.documentCodes()),
            d.staffCodePrefix(),
            d.internCodePrefix(),
            d.serviceCodePrefix(),
            null
        );
    }

    /** Trim, rỗng thành null, kiểm tra trên giá trị hiệu lực; trả DTO đã chuẩn hóa (không có {@code defaults}). */
    public static NumberingSettingsDTO normalizeAndValidate(NumberingSettingsDTO in) {
        String prefix = clean(in.documentPrefix());
        Map<DocumentType, String> codes = new EnumMap<>(DocumentType.class);
        for (DocumentType t : DocumentType.values()) {
            String c = in.documentCodes() == null ? null : clean(in.documentCodes().get(t));
            if (c != null) {
                codes.put(t, c);
            }
        }
        String staff = clean(in.staffCodePrefix());
        String intern = clean(in.internCodePrefix());
        String service = clean(in.serviceCodePrefix());

        if (prefix != null && !DOC.matcher(prefix).matches()) {
            throw docInvalid();
        }
        for (String c : codes.values()) {
            if (!DOC.matcher(c).matches()) {
                throw docInvalid();
            }
        }
        for (String p : new String[] { staff, intern, service }) {
            if (p != null && !CODE.matcher(p).matches()) {
                throw new EmployeeRuleException(
                    "Tiền tố mã nhân viên chỉ gồm chữ A–Z và số, bắt đầu bằng chữ, tối đa 10 ký tự.",
                    "numberingcodeinvalid"
                );
            }
        }

        Set<String> effectiveDoc = new HashSet<>();
        for (DocumentType t : DocumentType.values()) {
            effectiveDoc.add(codes.getOrDefault(t, t.code()));
        }
        if (effectiveDoc.size() != DocumentType.values().length) {
            throw new EmployeeRuleException("Mã các loại văn bản phải khác nhau.", "numberingdocduplicate");
        }
        Set<String> effectivePrefixes = new HashSet<>();
        effectivePrefixes.add(staff == null ? NumberingSettings.DEFAULT_STAFF_PREFIX : staff);
        effectivePrefixes.add(intern == null ? NumberingSettings.DEFAULT_INTERN_PREFIX : intern);
        effectivePrefixes.add(service == null ? NumberingSettings.DEFAULT_SERVICE_PREFIX : service);
        if (effectivePrefixes.size() != 3) {
            throw new EmployeeRuleException("Ba tiền tố mã nhân viên phải khác nhau.", "numberingcodeduplicate");
        }
        for (String a : effectivePrefixes) {
            for (String b : effectivePrefixes) {
                if (!a.equals(b) && b.startsWith(a)) {
                    throw new EmployeeRuleException(
                        "Tiền tố mã nhân viên không được là phần đầu của tiền tố khác (vd ABC và ABCNV).",
                        "numberingcodeoverlap"
                    );
                }
            }
        }
        return new NumberingSettingsDTO(prefix, codes, staff, intern, service, null);
    }

    private static EmployeeRuleException docInvalid() {
        return new EmployeeRuleException(
            "Tiền tố và mã loại văn bản chỉ gồm chữ in hoa, số, gạch ngang, tối đa 10 ký tự, không có dấu \"/\".",
            "numberingdocinvalid"
        );
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = Normalizer.normalize(s, Normalizer.Form.NFC).trim();
        return t.isEmpty() ? null : t;
    }
}
