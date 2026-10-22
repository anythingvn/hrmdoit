package vn.doit.hr.leave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SickLeaveLimitTest {

    @Test
    void tiers() {
        assertEquals(30, SickLeaveLimit.daysPerYear(0));
        assertEquals(30, SickLeaveLimit.daysPerYear(14));
        assertEquals(40, SickLeaveLimit.daysPerYear(15));
        assertEquals(40, SickLeaveLimit.daysPerYear(29));
        assertEquals(60, SickLeaveLimit.daysPerYear(30));
    }

    @Test
    void personalLeave() {
        assertEquals(3, PersonalLeaveReason.KET_HON.days());
        assertEquals(1, PersonalLeaveReason.CON_KET_HON.days());
        assertEquals(3, PersonalLeaveReason.TANG.days());
    }
}
