package vn.doit.hr.contract;

import com.deepoove.poi.XWPFTemplate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Điền mẫu HĐLĐ (.docx có thẻ {{...}}) bằng poi-tl.
 * HR có thể tự sửa câu chữ trong Word, chỉ cần giữ nguyên các thẻ {{...}}.
 */
public class ContractGenerator {

    public void render(InputStream template, ContractData data, OutputStream out) throws IOException {
        try (XWPFTemplate t = XWPFTemplate.compile(template).render(ContractFields.toMap(data))) {
            t.write(out);
        }
    }
}
