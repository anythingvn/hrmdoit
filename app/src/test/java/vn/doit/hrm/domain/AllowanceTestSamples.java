package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class AllowanceTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Allowance getAllowanceSample1() {
        return new Allowance().id(1L).name("name1").amountPerMonth(1L);
    }

    public static Allowance getAllowanceSample2() {
        return new Allowance().id(2L).name("name2").amountPerMonth(2L);
    }

    public static Allowance getAllowanceRandomSampleGenerator() {
        return new Allowance()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .amountPerMonth(longCount.incrementAndGet());
    }
}
