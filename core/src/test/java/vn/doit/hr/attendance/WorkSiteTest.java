package vn.doit.hr.attendance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkSiteTest {

    private static WorkSite siteWithIps(String... ips) {
        return new WorkSite("HQ", "Văn phòng", new GeoPoint(21.0278, 105.8360), 100, Set.of(), Set.of(ips));
    }

    @Test
    void cidrMatches() {
        WorkSite s = siteWithIps("203.0.113.0/24");
        assertTrue(s.hasIp("203.0.113.77"));
        assertFalse(s.hasIp("203.0.114.1"));
    }

    @Test
    void singleIpMatchesOnlyItself() {
        WorkSite s = siteWithIps("203.0.113.10");
        assertTrue(s.hasIp("203.0.113.10"));
        assertTrue(s.hasIp(" 203.0.113.10 "));
        assertFalse(s.hasIp("203.0.113.11"));
    }

    @Test
    void ipv6Cidr() {
        WorkSite s = siteWithIps("2001:db8::/32");
        assertTrue(s.hasIp("2001:db8::1"));
        assertFalse(s.hasIp("2001:db9::1"));
        assertFalse(s.hasIp("203.0.113.1"));
    }

    @Test
    void garbageNeverMatchesAndNeverResolvesDns() {
        WorkSite s = siteWithIps("203.0.113.0/24", "10.0.0.0/99", "not-an-ip/8");
        assertFalse(s.hasIp("example.com"));
        assertFalse(s.hasIp("localhost"));
        assertFalse(s.hasIp(""));
        assertFalse(s.hasIp(null));
        assertFalse(s.hasIp("10.0.0.5"));      // /99 không hợp lệ
        assertFalse(s.hasIp("203.0.113.256"));
    }

    @Test
    void zeroPrefixMatchesSameFamilyOnly() {
        WorkSite s = siteWithIps("0.0.0.0/0");
        assertTrue(s.hasIp("198.51.100.7"));
        assertFalse(s.hasIp("2001:db8::1"));
    }
}
