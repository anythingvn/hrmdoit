package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class EmployeeDocumentTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);

    public static EmployeeDocument getEmployeeDocumentSample1() {
        return new EmployeeDocument()
            .id(1L)
            .fileName("fileName1")
            .contentType("contentType1")
            .sizeBytes(1L)
            .storagePath("storagePath1")
            .uploadedBy("uploadedBy1");
    }

    public static EmployeeDocument getEmployeeDocumentSample2() {
        return new EmployeeDocument()
            .id(2L)
            .fileName("fileName2")
            .contentType("contentType2")
            .sizeBytes(2L)
            .storagePath("storagePath2")
            .uploadedBy("uploadedBy2");
    }

    public static EmployeeDocument getEmployeeDocumentRandomSampleGenerator() {
        return new EmployeeDocument()
            .id(longCount.incrementAndGet())
            .fileName(UUID.randomUUID().toString())
            .contentType(UUID.randomUUID().toString())
            .sizeBytes(longCount.incrementAndGet())
            .storagePath(UUID.randomUUID().toString())
            .uploadedBy(UUID.randomUUID().toString());
    }
}
