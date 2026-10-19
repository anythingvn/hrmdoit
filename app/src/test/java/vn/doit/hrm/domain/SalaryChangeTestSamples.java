package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SalaryChangeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static SalaryChange getSalaryChangeSample1() {
        return new SalaryChange().id(1L).amount(1L).grossAmount(1L).reviewCycleMonths(1).note("note1");
    }

    public static SalaryChange getSalaryChangeSample2() {
        return new SalaryChange().id(2L).amount(2L).grossAmount(2L).reviewCycleMonths(2).note("note2");
    }

    public static SalaryChange getSalaryChangeRandomSampleGenerator() {
        return new SalaryChange()
            .id(longCount.incrementAndGet())
            .amount(longCount.incrementAndGet())
            .grossAmount(longCount.incrementAndGet())
            .reviewCycleMonths(intCount.incrementAndGet())
            .note(UUID.randomUUID().toString());
    }
}
