package vn.doit.hr.contract;

import com.deepoove.poi.XWPFTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Điền bất kỳ mẫu .docx có thẻ {{...}} (HĐLĐ, quyết định cử nhân sự, ...) bằng poi-tl. */
public class DocxTemplateRenderer {

    public void render(InputStream template, Map<String, ?> fields, OutputStream out) throws IOException {
        try (XWPFTemplate t = XWPFTemplate.compile(template).render(fields)) {
            t.write(out);
        }
    }
}
