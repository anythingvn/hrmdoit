package vn.doit.hrm.service.backup;

import java.util.regex.Pattern;
import vn.doit.hrm.service.EmployeeRuleException;

/** Kiểm tra khóa công khai age (age1 + 58 ký tự bech32 thường). */
public final class AgeRecipient {

    private static final Pattern PATTERN = Pattern.compile("age1[02-9ac-hj-np-z]{58}");

    private AgeRecipient() {}

    public static String validate(String recipient) {
        String s = recipient == null ? "" : recipient.trim();
        if (!PATTERN.matcher(s).matches()) {
            throw new EmployeeRuleException("Khóa mã hóa không hợp lệ (phải bắt đầu bằng age1).", "backupagerecipient");
        }
        return s;
    }
}
