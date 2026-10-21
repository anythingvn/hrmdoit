package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentContract;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.repository.DocumentSequenceRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.EmploymentContractRepository;

/** Đánh số văn bản: mỗi loại một dãy, chạy lại mỗi năm, không cấp trùng. */
@IntegrationTest
class DocumentNumberServiceIT {

    @Autowired
    private DocumentNumberService service;

    @Autowired
    private DocumentSequenceRepository sequenceRepository;

    @Autowired
    private EmploymentContractRepository contractRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TransactionTemplate tx;

    private final List<Long> createdEmployees = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void clean() {
        sequenceRepository.deleteAll();
        tx.executeWithoutResult(s -> {
            createdEmployees.forEach(id -> {
                contractRepository.findByEmployeeId(id).forEach(contractRepository::delete);
                employeeRepository.deleteById(id);
            });
        });
        createdEmployees.clear();
    }

    @Test
    void firstNumberOfYear() {
        assertThat(service.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/1");
        assertThat(service.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/2");
    }

    @Test
    void separateSequences() {
        service.next(DocumentType.HDLD, 2026);
        service.next(DocumentType.HDLD, 2026);
        assertThat(service.next(DocumentType.HDTV, 2026)).isEqualTo("DOIT/2026/HĐTV/1");
        assertThat(service.next(DocumentType.QD_CU_NHAN_SU, 2026)).isEqualTo("DOIT/2026/QĐCNS/1");
    }

    @Test
    void newYearRestarts() {
        service.next(DocumentType.HDLD, 2026);
        service.next(DocumentType.HDLD, 2026);
        assertThat(service.next(DocumentType.HDLD, 2027)).isEqualTo("DOIT/2027/HĐLĐ/1");
    }

    @Test
    void adminSetsStart() {
        service.setNextValue(DocumentType.HDLD, 2026, 112);
        assertThat(service.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/112");
        assertThatThrownBy(() -> service.setNextValue(DocumentType.HDLD, 2026, 112))
            .isInstanceOf(EmployeeRuleException.class)
            .extracting(e -> ((EmployeeRuleException) e).getErrorKey())
            .isEqualTo("sequencebelowused");
        // Đặt cao hơn số đã cấp thì được; chưa cấp số nào thì đặt lại thấp hơn cũng được.
        service.setNextValue(DocumentType.HDLD, 2026, 200);
        service.setNextValue(DocumentType.HDTV, 2026, 50);
        service.setNextValue(DocumentType.HDTV, 2026, 5);
        assertThat(service.next(DocumentType.HDTV, 2026)).isEqualTo("DOIT/2026/HĐTV/5");
    }

    @Test
    void skipsNumberTypedByHand() {
        Long employeeId = tx.execute(s -> {
            Employee e = new Employee()
                .code("SEQ-IT-1")
                .fullName("Người Thử Số")
                .personType(vn.doit.hrm.domain.enumeration.PersonType.NHAN_VIEN)
                .dependants(0)
                .status(vn.doit.hrm.domain.enumeration.EmploymentStatus.DANG_LAM_VIEC);
            employeeRepository.save(e);
            EmploymentContract c = new EmploymentContract()
                .contractNo("DOIT/2026/HĐLĐ/1")
                .type(vn.doit.hrm.domain.enumeration.ContractType.HDLD_KXDTH)
                .startDate(java.time.LocalDate.of(2026, 1, 1))
                .status(vn.doit.hrm.domain.enumeration.ContractStatus.DA_KY)
                .employee(e);
            contractRepository.save(c);
            return e.getId();
        });
        createdEmployees.add(employeeId);

        assertThat(service.isTaken("DOIT/2026/HĐLĐ/1")).isTrue();
        assertThat(service.isTaken("DOIT/2026/HĐLĐ/2")).isFalse();
        assertThat(service.next(DocumentType.HDLD, 2026)).isEqualTo("DOIT/2026/HĐLĐ/2");
    }

    @Test
    void concurrentNext() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> calls = IntStream.range(0, 10)
                .<Callable<String>>mapToObj(i -> () -> service.next(DocumentType.HDLD, 2026))
                .toList();
            List<String> numbers = new ArrayList<>();
            for (Future<String> f : pool.invokeAll(calls)) {
                numbers.add(f.get());
            }
            assertThat(numbers).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, 10).mapToObj(n -> "DOIT/2026/HĐLĐ/" + n).toList()
            );
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void contractTypesShareTheLabourSequence() {
        assertThat(DocumentType.forContract(vn.doit.hrm.domain.enumeration.ContractType.HDLD_XDTH)).isEqualTo(DocumentType.HDLD);
        assertThat(DocumentType.forContract(vn.doit.hrm.domain.enumeration.ContractType.HDLD_KXDTH)).isEqualTo(DocumentType.HDLD);
        assertThat(DocumentType.forContract(vn.doit.hrm.domain.enumeration.ContractType.HDDV)).isEqualTo(DocumentType.HDDV);
    }
}
