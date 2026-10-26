package vn.doit.hrm.service.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.dto.NumberingSettingsDTO;

class NumberingRulesTest {

    private static NumberingSettingsDTO dto(String prefix, Map<DocumentType, String> codes, String staff, String intern, String service) {
        return new NumberingSettingsDTO(prefix, codes, staff, intern, service, null);
    }

    private static Map<DocumentType, String> codes(Object... kv) {
        Map<DocumentType, String> m = new EnumMap<>(DocumentType.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((DocumentType) kv[i], (String) kv[i + 1]);
        }
        return m;
    }

    private static void assertKey(NumberingSettingsDTO in, String key) {
        assertThatThrownBy(() -> NumberingRules.normalizeAndValidate(in)).isInstanceOfSatisfying(EmployeeRuleException.class, e ->
            assertThat(e.getErrorKey()).isEqualTo(key)
        );
    }

    @Test
    void acceptsVietnameseUppercase() {
        NumberingSettingsDTO out = NumberingRules.normalizeAndValidate(
            dto("ABC1", codes(DocumentType.HDLD, "HĐLĐ", DocumentType.QD_CU_NHAN_SU, "QĐ-CNS"), null, null, null)
        );
        assertThat(out.documentPrefix()).isEqualTo("ABC1");
        assertThat(out.documentCodes()).containsEntry(DocumentType.QD_CU_NHAN_SU, "QĐ-CNS");
    }

    @Test
    void rejectsSlashLongLowercase() {
        assertKey(dto("A/B", codes(), null, null, null), "numberingdocinvalid");
        assertKey(dto("ABCDEFGHIJK", codes(), null, null, null), "numberingdocinvalid");
        assertKey(dto(null, codes(DocumentType.HDLD, "hđlđ"), null, null, null), "numberingdocinvalid");
    }

    @Test
    void trimsAndBlanksToNull() {
        NumberingSettingsDTO out = NumberingRules.normalizeAndValidate(dto(" ABC ", codes(DocumentType.HDTV, "  "), "  ", null, null));
        assertThat(out.documentPrefix()).isEqualTo("ABC");
        assertThat(out.documentCodes()).isEmpty();
        assertThat(out.staffCodePrefix()).isNull();
    }

    @Test
    void rejectsDuplicateDocCodes() {
        assertKey(dto(null, codes(DocumentType.HDLD, "X", DocumentType.HDTV, "X"), null, null, null), "numberingdocduplicate");
        assertKey(dto(null, codes(DocumentType.HDTV, "HĐLĐ"), null, null, null), "numberingdocduplicate");
    }

    @Test
    void rejectsBadCodePrefix() {
        assertKey(dto(null, codes(), "1AB", null, null), "numberingcodeinvalid");
        assertKey(dto(null, codes(), "A-B", null, null), "numberingcodeinvalid");
        assertKey(dto(null, codes(), "ab", null, null), "numberingcodeinvalid");
    }

    @Test
    void rejectsDuplicateCodePrefix() {
        assertKey(dto(null, codes(), null, "DOIT", null), "numberingcodeduplicate");
    }

    @Test
    void rejectsOverlappingCodePrefix() {
        assertKey(dto(null, codes(), "ABCNV", "ABCTT", "ABC"), "numberingcodeoverlap");
        assertKey(dto(null, codes(), "NV", "NV1", null), "numberingcodeoverlap");
        assertKey(dto(null, codes(), null, null, "TT1"), "numberingcodeoverlap");
    }

    @Test
    void acceptsDefaultsAndDistinctPrefixes() {
        NumberingRules.normalizeAndValidate(dto(null, codes(), null, null, null));
        NumberingRules.normalizeAndValidate(dto(null, codes(), "NV", "TT", "DV"));
    }

    @Test
    void rejectsServiceEqualToDefaultStaffOrIntern() {
        assertKey(dto(null, codes(), null, null, "DOIT"), "numberingcodeduplicate");
        assertKey(dto(null, codes(), null, null, "TT"), "numberingcodeduplicate");
    }
}
