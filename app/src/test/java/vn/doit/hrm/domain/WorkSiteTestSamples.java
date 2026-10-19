package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class WorkSiteTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static WorkSite getWorkSiteSample1() {
        return new WorkSite().id(1L).name("name1").address("address1");
    }

    public static WorkSite getWorkSiteSample2() {
        return new WorkSite().id(2L).name("name2").address("address2");
    }

    public static WorkSite getWorkSiteRandomSampleGenerator() {
        return new WorkSite().id(longCount.incrementAndGet()).name(UUID.randomUUID().toString()).address(UUID.randomUUID().toString());
    }
}
