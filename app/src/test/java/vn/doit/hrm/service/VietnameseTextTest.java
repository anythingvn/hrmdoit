package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;

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
}
