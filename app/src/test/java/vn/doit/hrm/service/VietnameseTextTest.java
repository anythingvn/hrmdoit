package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class VietnameseTextTest {

    @Test
    void foldRemovesAccentsAndLowercases() {
        assertThat(VietnameseText.fold("Trần Minh Quân")).isEqualTo("tran minh quan");
    }

    @Test
    void foldHandlesDStrokeAndCollapsesSpaces() {
        assertThat(VietnameseText.fold("ĐỖ  Văn Bình")).isEqualTo("do van binh");
    }

    @Test
    void foldNullIsEmpty() {
        assertThat(VietnameseText.fold(null)).isEmpty();
    }

    @Test
    void normalizeCodeTrimsAndUppercases() {
        assertThat(EmployeeCodes.normalize(" nv0142 ")).isEqualTo("NV0142");
        assertThat(EmployeeCodes.normalize("  ")).isNull();
        assertThat(EmployeeCodes.normalize(null)).isNull();
    }

    @Test
    void sequencedCodeMatchesWhatNextReads() {
        assertThat(EmployeeCodes.isSequenced("DOIT", "DOIT2401")).isTrue();
        assertThat(EmployeeCodes.isSequenced("TT", "TT26105")).isTrue();
        assertThat(EmployeeCodes.isSequenced("DOIT", "DOIT241")).isFalse();
        assertThat(EmployeeCodes.isSequenced("DOIT", "TT2401")).isFalse();
        assertThat(EmployeeCodes.isSequenced("DOIT", "NV0142")).isFalse();
        assertThat(EmployeeCodes.next("DOIT", 2026, List.of("DOIT2417", "DOIT241", "TT2499"))).isEqualTo("DOIT2618");
    }
}
