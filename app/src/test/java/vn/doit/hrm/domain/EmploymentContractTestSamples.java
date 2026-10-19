package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EmploymentContractTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static EmploymentContract getEmploymentContractSample1() {
        return new EmploymentContract()
            .id(1L)
            .contractNo("contractNo1")
            .termMonths(1)
            .position("position1")
            .salary(1L)
            .grossAmount(1L)
            .raisePolicy("raisePolicy1")
            .signedFileId(1L);
    }

    public static EmploymentContract getEmploymentContractSample2() {
        return new EmploymentContract()
            .id(2L)
            .contractNo("contractNo2")
            .termMonths(2)
            .position("position2")
            .salary(2L)
            .grossAmount(2L)
            .raisePolicy("raisePolicy2")
            .signedFileId(2L);
    }

    public static EmploymentContract getEmploymentContractRandomSampleGenerator() {
        return new EmploymentContract()
            .id(longCount.incrementAndGet())
            .contractNo(UUID.randomUUID().toString())
            .termMonths(intCount.incrementAndGet())
            .position(UUID.randomUUID().toString())
            .salary(longCount.incrementAndGet())
            .grossAmount(longCount.incrementAndGet())
            .raisePolicy(UUID.randomUUID().toString())
            .signedFileId(longCount.incrementAndGet());
    }
}
