package vn.doit.hr.contract;

/** Đọc số tiền thành chữ tiếng Việt, vd 5.700.000 → "Năm triệu bảy trăm nghìn đồng". */
public final class VietnameseNumberWords {

    private static final String[] DIGITS = {"không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
    private static final String[] UNITS = {"", "nghìn", "triệu", "tỷ", "nghìn tỷ", "triệu tỷ"};

    private VietnameseNumberWords() {
    }

    public static String toWords(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Số tiền âm: " + amount);
        }
        if (amount == 0) {
            return "Không đồng";
        }
        int[] groups = new int[UNITS.length];
        int n = 0;
        for (long a = amount; a > 0; a /= 1000) {
            groups[n++] = (int) (a % 1000);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = n - 1; i >= 0; i--) {
            if (groups[i] == 0) {
                continue;
            }
            boolean full = i < n - 1; // không phải nhóm đầu → đọc đủ "không trăm", "linh"
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(readGroup(groups[i], full));
            if (!UNITS[i].isEmpty()) {
                sb.append(' ').append(UNITS[i]);
            }
        }
        sb.append(" đồng");
        return Character.toUpperCase(sb.charAt(0)) + sb.substring(1);
    }

    private static String readGroup(int g, boolean full) {
        int h = g / 100;
        int t = (g / 10) % 10;
        int u = g % 10;
        StringBuilder sb = new StringBuilder();
        if (h > 0 || full) {
            sb.append(DIGITS[h]).append(" trăm");
        }
        if (t == 0) {
            if (u > 0) {
                if (!sb.isEmpty()) {
                    sb.append(" linh");
                }
                append(sb, DIGITS[u]);
            }
        } else {
            append(sb, t == 1 ? "mười" : DIGITS[t] + " mươi");
            if (u == 1 && t > 1) {
                append(sb, "mốt");
            } else if (u == 5) {
                append(sb, "lăm");
            } else if (u > 0) {
                append(sb, DIGITS[u]);
            }
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String word) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(word);
    }
}
