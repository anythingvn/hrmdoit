package vn.doit.hr.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class TemplateTagsTest {

    @Test
    void contractTemplateHasKnownTagsOnly() throws Exception {
        TemplateTags.TemplateScan scan = scanResource("/templates/DOIT_HDLD_template.docx");
        assertTrue(scan.tags().contains("hoTen"));
        assertTrue(scan.tags().contains("mucLuongBangChu"));
        assertEquals(List.of(), scan.brokenTags());
        assertTrue(TemplateTags.CONTRACT_TAGS.containsAll(scan.tags()), "thẻ lạ: " + scan.tags());
    }

    @Test
    void decisionTemplateHasKnownTagsOnly() throws Exception {
        TemplateTags.TemplateScan scan = scanResource("/templates/DOIT_QD_CuNhanSu_template.docx");
        assertTrue(scan.tags().contains("soQuyetDinh"));
        assertTrue(scan.tags().contains("donVi"));
        assertEquals(List.of(), scan.brokenTags());
        assertTrue(TemplateTags.DECISION_TAGS.containsAll(scan.tags()), "thẻ lạ: " + scan.tags());
    }

    @Test
    void tagSplitAcrossDifferentlyFormattedRunsIsBroken() throws Exception {
        byte[] docx = docx(p -> {
            XWPFRun a = p.createRun();
            a.setText("Họ tên: {{hoT");
            XWPFRun b = p.createRun();
            b.setBold(true);
            b.setText("en}} và {{viTri}}");
        });
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertEquals(List.of("hoTen"), scan.brokenTags());
        assertTrue(scan.tags().contains("viTri"));
    }

    @Test
    void tagSplitAcrossSameFormattedRunsIsFine() throws Exception {
        byte[] docx = docx(p -> {
            p.createRun().setText("{{hoT");
            p.createRun().setText("en}}");
        });
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertEquals(List.of(), scan.brokenTags());
        assertTrue(scan.tags().contains("hoTen"));
    }

    @Test
    void languageOrProofingMarksAreNotFormatting() throws Exception {
        byte[] docx = docx(p -> {
            p.createRun().setText("{{hoT");
            XWPFRun b = p.createRun();
            b.getCTR().addNewRPr().addNewLang().setVal("vi-VN");
            b.getCTR().getRPr().addNewNoProof();
            b.setText("en}}");
        });
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertEquals(List.of(), scan.brokenTags());
        assertTrue(scan.tags().contains("hoTen"));
    }

    @Test
    void unclosedTagIsBroken() throws Exception {
        byte[] docx = docx(p -> p.createRun().setText("Ngày {{tuNgay đến {{denNgay}}"));
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertEquals(List.of("tuNgay"), scan.brokenTags());
    }

    @Test
    void unknownTagIsListed() throws Exception {
        byte[] docx = docx(p -> p.createRun().setText("{{abc}}"));
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertTrue(scan.tags().contains("abc"));
        assertTrue(!TemplateTags.CONTRACT_TAGS.contains("abc"));
    }

    @Test
    void tagsInHeaderAndTableAreFound() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Số {{soHopDong}}");
            doc.createTable(1, 1).getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("{{boPhan}}");
            doc.write(out);
            docx = out.toByteArray();
        }
        TemplateTags.TemplateScan scan = TemplateTags.scan(new ByteArrayInputStream(docx));
        assertTrue(scan.tags().contains("soHopDong"));
        assertTrue(scan.tags().contains("boPhan"));
    }

    private static TemplateTags.TemplateScan scanResource(String path) throws Exception {
        try (InputStream in = TemplateTagsTest.class.getResourceAsStream(path)) {
            return TemplateTags.scan(in);
        }
    }

    private static byte[] docx(java.util.function.Consumer<XWPFParagraph> fill) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(doc.createParagraph());
            doc.write(out);
            return out.toByteArray();
        }
    }
}
