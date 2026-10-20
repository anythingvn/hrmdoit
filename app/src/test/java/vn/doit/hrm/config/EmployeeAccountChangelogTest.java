package vn.doit.hrm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Ràng buộc duy nhất {@code ux_employee__user_id} có changeset kiểm tra chạy trước: dữ liệu cũ trùng {@code user_id} thì Liquibase
 * dừng (HALT) với thông báo rõ thay vì lỗi SQL (M11).
 */
class EmployeeAccountChangelogTest {

    @Test
    void duplicateUserIdCheckRunsBeforeUniqueConstraint() throws Exception {
        Document doc;
        try (InputStream in = getClass().getResourceAsStream("/config/liquibase/changelog/20261020000100_employee_account.xml")) {
            assertThat(in).isNotNull();
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            doc = f.newDocumentBuilder().parse(in);
        }
        NodeList sets = doc.getElementsByTagName("changeSet");
        List<String> ids = new ArrayList<>();
        Element check = null;
        for (int i = 0; i < sets.getLength(); i++) {
            Element e = (Element) sets.item(i);
            ids.add(e.getAttribute("id"));
            if ("20261020000100-2-check".equals(e.getAttribute("id"))) {
                check = e;
            }
        }
        assertThat(ids.indexOf("20261020000100-2-check")).isGreaterThanOrEqualTo(0).isLessThan(ids.indexOf("20261020000100-2"));
        assertThat(check).isNotNull();
        Element pre = (Element) check.getElementsByTagName("preConditions").item(0);
        assertThat(pre.getAttribute("onFail")).isEqualTo("HALT");
        assertThat(pre.getAttribute("onFailMessage")).contains("nhiều hồ sơ nhân viên");
        String sql = pre.getElementsByTagName("sqlCheck").item(0).getTextContent();
        assertThat(sql).contains("group by user_id").contains("having count(*) > 1");
    }
}
