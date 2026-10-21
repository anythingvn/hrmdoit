package vn.doit.hr.contract;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import vn.doit.hr.assignment.Assignment;
import vn.doit.hr.assignment.AssignmentDecision;
import vn.doit.hr.assignment.WorkLocationType;

/**
 * Quét các thẻ {{...}} trong mẫu .docx (thân văn bản, bảng, header, footer) để kiểm tra mẫu trước khi nhận.
 * <p>
 * Thẻ "bị cắt" là thẻ không đóng trong cùng đoạn ({@code {{tuNgay} ...}), hoặc thẻ trải qua nhiều run có định dạng khác nhau
 * (HR bôi đậm/đổi font một nửa thẻ trong Word). Thẻ trải qua nhiều run cùng định dạng thì vẫn điền được.
 */
public final class TemplateTags {

    /** Thẻ dùng được trong mẫu hợp đồng: các khóa của {@link ContractFields#toMap}. */
    public static final Set<String> CONTRACT_TAGS = Set.copyOf(ContractFields.toMap(new ContractData(
            "", "x", "", null, "", "", "", "", "", null, "", "", "", null, LocalDate.of(2026, 1, 1), "", "", 1,
            List.of(), "", "x")).keySet());

    /** Thẻ dùng được trong mẫu quyết định cử nhân sự: các khóa của {@link AssignmentDecision#toFields}. */
    public static final Set<String> DECISION_TAGS = Set.copyOf(new AssignmentDecision("", LocalDate.of(2026, 1, 1), "", "",
            "", "", "", "", "",
            new Assignment(0, "", WorkLocationType.CONG_TAC, "", "", LocalDate.of(2026, 1, 1), null, null, null), "", "")
            .toFields().keySet());

    /**
     * @param tags thẻ đọc được (đã bỏ khoảng trắng hai đầu), xếp theo tên
     * @param brokenTags thẻ bị cắt, theo thứ tự xuất hiện, không lặp
     */
    public record TemplateScan(Set<String> tags, List<String> brokenTags) {
    }

    private TemplateTags() {
    }

    public static TemplateScan scan(InputStream docx) throws IOException {
        Set<String> tags = new TreeSet<>();
        Set<String> broken = new LinkedHashSet<>();
        try (XWPFDocument doc = new XWPFDocument(docx)) {
            scanBody(doc, tags, broken);
            doc.getHeaderList().forEach(h -> scanBody(h, tags, broken));
            doc.getFooterList().forEach(f -> scanBody(f, tags, broken));
        }
        return new TemplateScan(tags, new ArrayList<>(broken));
    }

    private static void scanBody(IBody body, Set<String> tags, Set<String> broken) {
        for (IBodyElement e : body.getBodyElements()) {
            if (e instanceof XWPFParagraph p) {
                scanParagraph(p, tags, broken);
            } else if (e instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        scanBody(cell, tags, broken);
                    }
                }
            }
        }
    }

    private static void scanParagraph(XWPFParagraph p, Set<String> tags, Set<String> broken) {
        List<XWPFRun> runs = p.getRuns();
        StringBuilder text = new StringBuilder();
        List<Integer> runAt = new ArrayList<>();
        for (int r = 0; r < runs.size(); r++) {
            String t = runs.get(r).text();
            text.append(t);
            for (int i = 0; i < t.length(); i++) {
                runAt.add(r);
            }
        }
        String s = text.toString();
        int from = 0;
        while (true) {
            int open = s.indexOf("{{", from);
            if (open < 0) {
                return;
            }
            int close = s.indexOf("}}", open + 2);
            int nextOpen = s.indexOf("{{", open + 2);
            if (close < 0 || (nextOpen >= 0 && nextOpen < close)) {
                String rest = s.substring(open + 2, nextOpen >= 0 ? nextOpen : s.length()).trim();
                broken.add(rest.isEmpty() ? "{{" : rest.split("\\s+")[0]);
                from = open + 2;
                continue;
            }
            String name = s.substring(open + 2, close).trim();
            if (sameFormat(runs, runAt.get(open), runAt.get(close + 1))) {
                tags.add(name);
            } else {
                broken.add(name);
            }
            from = close + 2;
        }
    }

    private static boolean sameFormat(List<XWPFRun> runs, int first, int last) {
        String format = format(runs.get(first));
        for (int r = first + 1; r <= last; r++) {
            if (!runs.get(r).text().isEmpty() && !format.equals(format(runs.get(r)))) {
                return false;
            }
        }
        return true;
    }

    /** Định dạng nhìn thấy được của run; bỏ ngôn ngữ và đánh dấu kiểm tra chính tả mà Word tự thêm khi gõ. */
    private static String format(XWPFRun run) {
        CTRPr rPr = run.getCTR().getRPr();
        CTRPr copy = rPr == null ? CTRPr.Factory.newInstance() : (CTRPr) rPr.copy();
        while (copy.sizeOfLangArray() > 0) {
            copy.removeLang(0);
        }
        while (copy.sizeOfNoProofArray() > 0) {
            copy.removeNoProof(0);
        }
        // Khai báo namespace còn sót lại sau khi xóa phần tử không phải định dạng.
        return copy.xmlText().replaceAll("\\s+xmlns(:\\w+)?=\"[^\"]*\"", "");
    }
}
