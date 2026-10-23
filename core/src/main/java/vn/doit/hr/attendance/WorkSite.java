package vn.doit.hr.attendance;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Một điểm chấm công: văn phòng, trụ sở khách hàng, chi nhánh...
 *
 * @param radiusMeters bán kính cho phép quanh tâm (văn phòng trong tòa nhà thường 50–150 m)
 * @param wifiBssids địa chỉ MAC của các điểm phát WiFi tại đây (dạng aa:bb:cc:dd:ee:ff)
 * @param publicIps IP public của mạng tại đây (server tự thấy, điện thoại không giả được)
 */
public record WorkSite(String id, String name, GeoPoint center, int radiusMeters, Set<String> wifiBssids, Set<String> publicIps) {

    /** Bán kính mặc định do DOIT chọn (GPS trong ngõ phố thường lệch vài chục đến hơn trăm mét). */
    public static final int DEFAULT_RADIUS_METERS = 200;

    /** Điểm chấm công với bán kính mặc định {@value #DEFAULT_RADIUS_METERS} m. */
    public static WorkSite withDefaultRadius(String id, String name, GeoPoint center, Set<String> wifiBssids, Set<String> publicIps) {
        return new WorkSite(id, name, center, DEFAULT_RADIUS_METERS, wifiBssids, publicIps);
    }

    public WorkSite {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("Bán kính phải > 0");
        }
        wifiBssids = wifiBssids == null ? Set.of()
                : wifiBssids.stream().map(WorkSite::normalizeBssid).collect(Collectors.toUnmodifiableSet());
        publicIps = publicIps == null ? Set.of() : Set.copyOf(publicIps);
    }

    public boolean hasWifi(String bssid) {
        return bssid != null && wifiBssids.contains(normalizeBssid(bssid));
    }

    /** IP khớp một mục trong {@code publicIps}: IP đơn (so đúng chuỗi) hoặc dải CIDR IPv4/IPv6. */
    public boolean hasIp(String ip) {
        if (ip == null) {
            return false;
        }
        String candidate = ip.trim();
        if (publicIps.contains(candidate)) {
            return true;
        }
        byte[] addr = parseLiteral(candidate);
        if (addr == null) {
            return false;
        }
        for (String entry : publicIps) {
            int slash = entry.indexOf('/');
            if (slash > 0 && cidrContains(entry.substring(0, slash).trim(), entry.substring(slash + 1).trim(), addr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cidrContains(String netText, String prefixText, byte[] addr) {
        byte[] net = parseLiteral(netText);
        if (net == null || net.length != addr.length || !prefixText.matches("\\d{1,3}")) {
            return false;
        }
        int prefix = Integer.parseInt(prefixText);
        if (prefix > net.length * 8) {
            return false;
        }
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (net[i] != addr[i]) {
                return false;
            }
        }
        int rest = prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xFF << (8 - rest)) & 0xFF;
        return (net[fullBytes] & mask) == (addr[fullBytes] & mask);
    }

    /**
     * Đổi chuỗi IP dạng số sang byte (4 byte IPv4, 16 byte IPv6); null nếu không hợp lệ.
     * Tự phân tích từng ký tự, không gọi {@code InetAddress.getByName} nên không bao giờ tra DNS.
     * Địa chỉ IPv4-mapped (::ffff:a.b.c.d) được coi là chính địa chỉ IPv4 a.b.c.d (cố ý),
     * để khớp các dải CIDR IPv4 khi máy chủ nhìn thấy kết nối dưới dạng IPv6.
     */
    static byte[] parseLiteral(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.indexOf(':') < 0 ? parseV4(text) : parseV6(text);
    }

    private static byte[] parseV4(String text) {
        if (!text.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return null;
        }
        String[] parts = text.split("\\.");
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            int v = Integer.parseInt(parts[i]);
            if (v > 255) {
                return null;
            }
            out[i] = (byte) v;
        }
        return out;
    }

    private static byte[] parseV6(String text) {
        int dc = text.indexOf("::");
        if (dc != text.lastIndexOf("::") || text.contains(":::")) {
            return null;
        }
        int[] head;
        int[] tail;
        if (dc < 0) {
            head = groups(text, true);
            tail = new int[0];
            if (head == null || head.length != 8) {
                return null;
            }
        } else {
            head = groups(text.substring(0, dc), false);
            tail = groups(text.substring(dc + 2), true);
            if (head == null || tail == null || head.length + tail.length > 7) {
                return null;
            }
        }
        int[] all = new int[8];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, 8 - tail.length, tail.length);
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[2 * i] = (byte) (all[i] >> 8);
            out[2 * i + 1] = (byte) all[i];
        }
        boolean mapped = true;
        for (int i = 0; i < 10; i++) {
            mapped &= out[i] == 0;
        }
        if (mapped && out[10] == (byte) 0xFF && out[11] == (byte) 0xFF) {
            return Arrays.copyOfRange(out, 12, 16);
        }
        return out;
    }

    /** Các nhóm 16 bit ngăn bởi ':' (nhóm cuối có thể là IPv4 = 2 nhóm); chuỗi rỗng = không nhóm; sai = null. */
    private static int[] groups(String part, boolean allowV4Last) {
        if (part.isEmpty()) {
            return new int[0];
        }
        String[] items = part.split(":", -1);
        int[] out = new int[items.length + 1];
        int n = 0;
        for (int i = 0; i < items.length; i++) {
            String g = items[i];
            if (allowV4Last && i == items.length - 1 && g.indexOf('.') >= 0) {
                byte[] v4 = parseV4(g);
                if (v4 == null) {
                    return null;
                }
                out[n++] = ((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF);
                out[n++] = ((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF);
            } else if (g.matches("[0-9a-fA-F]{1,4}")) {
                out[n++] = Integer.parseInt(g, 16);
            } else {
                return null;
            }
        }
        return Arrays.copyOf(out, n);
    }

    static String normalizeBssid(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', ':');
    }
}
