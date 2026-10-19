package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class WorkAssignmentTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static WorkAssignment getWorkAssignmentSample1() {
        return new WorkAssignment().id(1L).unit("unit1").address("address1").decisionNo("decisionNo1").note("note1");
    }

    public static WorkAssignment getWorkAssignmentSample2() {
        return new WorkAssignment().id(2L).unit("unit2").address("address2").decisionNo("decisionNo2").note("note2");
    }

    public static WorkAssignment getWorkAssignmentRandomSampleGenerator() {
        return new WorkAssignment()
            .id(longCount.incrementAndGet())
            .unit(UUID.randomUUID().toString())
            .address(UUID.randomUUID().toString())
            .decisionNo(UUID.randomUUID().toString())
            .note(UUID.randomUUID().toString());
    }
}
