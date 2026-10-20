package vn.doit.hrm.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EmployeeTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + 2L * Integer.MAX_VALUE);
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Employee getEmployeeSample1() {
        return new Employee()
            .id(1L)
            .code("code1")
            .fullName("fullName1")
            .searchText("searchText1")
            .email("email1")
            .phone("phone1")
            .residence("residence1")
            .education("education1")
            .idNumber("idNumber1")
            .idIssuePlace("idIssuePlace1")
            .taxCode("taxCode1")
            .bankAccount("bankAccount1")
            .bankName("bankName1")
            .socialInsuranceNo("socialInsuranceNo1")
            .socialInsuranceSalary(1L)
            .dependants(1)
            .position("position1")
            .terminationReason("terminationReason1");
    }

    public static Employee getEmployeeSample2() {
        return new Employee()
            .id(2L)
            .code("code2")
            .fullName("fullName2")
            .searchText("searchText2")
            .email("email2")
            .phone("phone2")
            .residence("residence2")
            .education("education2")
            .idNumber("idNumber2")
            .idIssuePlace("idIssuePlace2")
            .taxCode("taxCode2")
            .bankAccount("bankAccount2")
            .bankName("bankName2")
            .socialInsuranceNo("socialInsuranceNo2")
            .socialInsuranceSalary(2L)
            .dependants(2)
            .position("position2")
            .terminationReason("terminationReason2");
    }

    public static Employee getEmployeeRandomSampleGenerator() {
        return new Employee()
            .id(longCount.incrementAndGet())
            .code(UUID.randomUUID().toString())
            .fullName(UUID.randomUUID().toString())
            .searchText(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .phone(UUID.randomUUID().toString())
            .residence(UUID.randomUUID().toString())
            .education(UUID.randomUUID().toString())
            .idNumber(UUID.randomUUID().toString())
            .idIssuePlace(UUID.randomUUID().toString())
            .taxCode(UUID.randomUUID().toString())
            .bankAccount(UUID.randomUUID().toString())
            .bankName(UUID.randomUUID().toString())
            .socialInsuranceNo(UUID.randomUUID().toString())
            .socialInsuranceSalary(longCount.incrementAndGet())
            .dependants(intCount.incrementAndGet())
            .position(UUID.randomUUID().toString())
            .terminationReason(UUID.randomUUID().toString());
    }
}
