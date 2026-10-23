package vn.doit.hr.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.doit.hr.attendance.DayCredit.Hours;
import vn.doit.hr.attendance.DayCredit.Result;
import vn.doit.hr.attendance.DayCredit.Rules;
import vn.doit.hr.attendance.DayCredit.Status;
import vn.doit.hr.leave.DayPart;

class DayCreditTest {

    private static final Hours COMPANY = new Hours(t("08:00"), t("17:00"), t("12:00"), t("13:00"));
    private static final Hours CUSTOMER = new Hours(t("08:30"), t("17:30"), t("12:00"), t("13:00"));
    private static final Rules RULES = new Rules(5, 90);

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    private static Result day(String in, String out) {
        return DayCredit.compute(List.of(t(in)), List.of(t(out)), COMPANY, RULES, null, false);
    }

    private static BigDecimal c(String v) {
        return new BigDecimal(v);
    }

    private static void assertCredit(String expected, Result r) {
        assertEquals(0, c(expected).compareTo(r.workCredit()), "credit " + r.workCredit());
    }

    @Test
    void onTime() {
        Result r = day("07:55", "17:05");
        assertEquals(Status.DI_LAM, r.status());
        assertCredit("1", r);
        assertEquals(0, r.lateMinutes());
        assertEquals(0, r.earlyMinutes());
    }

    @Test
    void lateWithinThreshold() {
        Result r = day("09:30", "17:00");
        assertEquals(Status.DI_LAM, r.status());
        assertCredit("1", r);
        assertEquals(90, r.lateMinutes());
    }

    @Test
    void lateBeyondThreshold() {
        Result r = day("09:31", "17:00");
        assertEquals(Status.DI_LAM, r.status());
        assertCredit("0.5", r);
        assertEquals(91, r.lateMinutes());
    }

    @Test
    void earlyWithinThreshold() {
        Result r = day("08:00", "15:30");
        assertCredit("1", r);
        assertEquals(90, r.earlyMinutes());
    }

    @Test
    void earlyBeyondThreshold() {
        Result r = day("08:00", "15:29");
        assertCredit("0.5", r);
        assertEquals(91, r.earlyMinutes());
    }

    @Test
    void lateAndEarlyBeyond() {
        Result r = day("09:40", "15:20");
        assertEquals(Status.VANG, r.status());
        assertCredit("0", r);
    }

    @Test
    void lessThanHalfScheduled() {
        Result r = day("08:00", "11:00");
        assertEquals(Status.VANG, r.status());
        assertCredit("0", r);
    }

    @Test
    void missingOut() {
        Result r = DayCredit.compute(List.of(t("08:00")), List.of(), COMPANY, RULES, null, false);
        assertEquals(Status.THIEU_GIO, r.status());
        assertNull(r.workCredit());
    }

    @Test
    void missingIn() {
        Result r = DayCredit.compute(List.of(), List.of(t("17:00")), COMPANY, RULES, null, false);
        assertEquals(Status.THIEU_GIO, r.status());
        assertNull(r.workCredit());
    }

    @Test
    void nothingEntered() {
        Result r = DayCredit.compute(List.of(), List.of(), COMPANY, RULES, null, false);
        assertEquals(Status.CHUA_NHAP, r.status());
    }

    @Test
    void markedAbsent() {
        Result r = DayCredit.compute(List.of(), List.of(), COMPANY, RULES, null, true);
        assertEquals(Status.VANG, r.status());
        assertCredit("0", r);
    }

    @Test
    void morningLeaveAfternoonWork() {
        Result r = DayCredit.compute(List.of(t("13:00")), List.of(t("17:00")), COMPANY, RULES, DayPart.SANG, false);
        assertEquals(Status.DI_LAM, r.status());
        assertCredit("0.5", r);
        Result late = DayCredit.compute(List.of(t("14:45")), List.of(t("17:00")), COMPANY, RULES, DayPart.SANG, false);
        assertCredit("0.5", late);
        assertEquals(105, late.lateMinutes());
    }

    @Test
    void afternoonLeave() {
        Result r = DayCredit.compute(List.of(t("08:00")), List.of(t("12:00")), COMPANY, RULES, DayPart.CHIEU, false);
        assertEquals(Status.DI_LAM, r.status());
        assertCredit("0.5", r);
    }

    @Test
    void customerHours() {
        Result a = DayCredit.compute(List.of(t("09:55")), List.of(t("17:30")), CUSTOMER, RULES, null, false);
        assertCredit("1", a);
        assertEquals(85, a.lateMinutes());
        Result b = DayCredit.compute(List.of(t("10:01")), List.of(t("17:30")), CUSTOMER, RULES, null, false);
        assertCredit("0.5", b);
    }

    @Test
    void multiplePunches() {
        Result r = DayCredit.compute(List.of(t("08:10"), t("07:58")), List.of(t("16:00"), t("17:02")),
                COMPANY, RULES, null, false);
        assertEquals(t("07:58"), r.firstIn());
        assertEquals(t("17:02"), r.lastOut());
    }
}
