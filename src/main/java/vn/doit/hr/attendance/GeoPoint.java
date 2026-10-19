package vn.doit.hr.attendance;

/** Tọa độ WGS84 (độ thập phân). */
public record GeoPoint(double latitude, double longitude) {

    private static final double EARTH_RADIUS_M = 6_371_008.8; // bán kính trung bình (IUGG)

    public GeoPoint {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Tọa độ không hợp lệ: " + latitude + ", " + longitude);
        }
    }

    /** Khoảng cách mặt đất (Haversine), mét. Sai số < 0,5% – đủ cho bán kính chấm công vài chục đến vài trăm mét. */
    public double distanceMeters(GeoPoint o) {
        double p1 = Math.toRadians(latitude);
        double p2 = Math.toRadians(o.latitude);
        double dp = Math.toRadians(o.latitude - latitude);
        double dl = Math.toRadians(o.longitude - longitude);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
