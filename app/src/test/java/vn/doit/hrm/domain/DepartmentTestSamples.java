package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class DepartmentTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static Department getDepartmentSample1() {
        return new Department().id(1L).name("name1");
    }

    public static Department getDepartmentSample2() {
        return new Department().id(2L).name("name2");
    }

    public static Department getDepartmentRandomSampleGenerator() {
        return new Department().id(longCount.incrementAndGet()).name(UUID.randomUUID().toString());
    }
}
