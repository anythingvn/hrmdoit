package vn.doit.hrm.service.backup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.doit.hrm.service.EmployeeRuleException;

/** Lấy ID thư mục/Shared Drive từ link Google Drive hoặc ID trần. */
public final class SharedDriveRef {

    private static final Pattern FOLDER = Pattern.compile("/folders/([A-Za-z0-9_-]{10,100})");
    private static final Pattern BARE = Pattern.compile("[A-Za-z0-9_-]{10,100}");

    private SharedDriveRef() {}

    public static String parseId(String linkOrId) {
        String s = linkOrId == null ? "" : linkOrId.trim();
        Matcher m = FOLDER.matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        if (BARE.matcher(s).matches()) {
            return s;
        }
        throw new EmployeeRuleException("Link Shared Drive không hợp lệ.", "backupdrivelink");
    }
}
