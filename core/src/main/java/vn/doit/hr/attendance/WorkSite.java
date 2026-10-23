package vn.doit.hr.attendance;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
     * Đổi chuỗi IP dạng số sang byte; null nếu không phải IPv4/IPv6 thuần số.
     * Chỉ chấp nhận chuỗi chỉ có chữ số, a-f, '.', ':' nên không bao giờ tra DNS.
     */
    private static byte[] parseLiteral(String text) {
        if (text == null || text.isEmpty() || !text.matches("[0-9a-fA-F.:]+")) {
            return null;
        }
        if (text.indexOf(':') < 0) {
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
        try {
            // có ':' nên là IPv6 literal: JDK không bao giờ tra DNS cho dạng này
            return InetAddress.getByName(text).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    static String normalizeBssid(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace('-', ':');
    }
}
