package vn.doit.hrm.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import vn.doit.hrm.IntegrationTest;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/** Lịch làm việc trong tuần (Quản trị sửa) và ngày lễ theo năm (HR, Quản trị sửa). */
@IntegrationTest
@AutoConfigureMockMvc
class WorkCalendarResourceIT {

    private static final RequestPostProcessor ADMIN = user("admin-lich").authorities(() -> "ROLE_ADMIN");
    private static final RequestPostProcessor HR = user("hr-lich").authorities(() -> "ROLE_HR");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkCalendarService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @AfterEach
    void reset() {
        tx.executeWithoutResult(s -> {
            jdbc.update("delete from holiday");
            jdbc.update("update work_calendar_setting set working_days = 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY' where id = 1");
        });
    }

    @Test
    void defaultMondayToFriday() throws Exception {
        mockMvc
            .perform(get("/api/work-calendar").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workingDays.length()").value(5))
            .andExpect(jsonPath("$.workingDays[0]").value("MONDAY"))
            .andExpect(jsonPath("$.workingDays[4]").value("FRIDAY"));
        assertThat(service.isWorkingDay(LocalDate.of(2026, 10, 24))).isFalse();
        assertThat(service.isWorkingDay(LocalDate.of(2026, 10, 23))).isTrue();
    }

    @Test
    void adminChangesWorkingDays() throws Exception {
        String body = "{\"workingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\"]}";
        mockMvc.perform(put("/api/work-calendar").with(HR).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc
            .perform(put("/api/work-calendar").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workingDays.length()").value(6));
        assertThat(service.isWorkingDay(LocalDate.of(2026, 10, 24))).isTrue();
        mockMvc
            .perform(put("/api/work-calendar").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"workingDays\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.workingdaysrequired"));
    }

    @Test
    void holidaysPerYearAndCopy() throws Exception {
        String qk = "{\"day\":\"2026-09-02\",\"name\":\"Quốc khánh\"}";
        mockMvc.perform(post("/api/holidays").with(HR).contentType(MediaType.APPLICATION_JSON).content(qk)).andExpect(status().isCreated());
        assertThat(service.isWorkingDay(LocalDate.of(2026, 9, 2))).isFalse();
        mockMvc
            .perform(post("/api/holidays").with(HR).contentType(MediaType.APPLICATION_JSON).content(qk))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.holidayexists"));
        mockMvc
            .perform(post("/api/holidays").with(HR).contentType(MediaType.APPLICATION_JSON).content("{\"day\":\"2024-02-29\",\"name\":\"Ngày nhuận\"}"))
            .andExpect(status().isCreated());
        mockMvc
            .perform(post("/api/holidays").with(HR).contentType(MediaType.APPLICATION_JSON).content("{\"day\":\"2024-09-02\",\"name\":\"Quốc khánh\"}"))
            .andExpect(status().isCreated());

        mockMvc
            .perform(post("/api/holidays/copy").param("fromYear", "2024").param("toYear", "2025").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].day").value("2025-09-02"));
        mockMvc
            .perform(post("/api/holidays/copy").param("fromYear", "2026").param("toYear", "2027").with(HR))
            .andExpect(jsonPath("$[0].day").value("2027-09-02"))
            .andExpect(jsonPath("$[0].name").value("Quốc khánh"));

        String list = mockMvc
            .perform(get("/api/holidays").param("year", "2026").with(HR))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.read(list, "$[0].id");
        mockMvc
            .perform(put("/api/holidays/{id}", id).with(HR).contentType(MediaType.APPLICATION_JSON).content("{\"day\":\"2026-09-01\",\"name\":\"Nghỉ lễ Quốc khánh\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.day").value("2026-09-01"));
        mockMvc.perform(delete("/api/holidays/{id}", id).with(HR)).andExpect(status().isNoContent());
        assertThat(service.isWorkingDay(LocalDate.of(2026, 9, 1))).isTrue();
    }

    @Test
    void permissions() throws Exception {
        mockMvc.perform(get("/api/holidays").param("year", "2026").with(user("ketoan").authorities(() -> "ROLE_KE_TOAN"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/holidays").param("year", "2026").with(user("bgd").authorities(() -> "ROLE_BGD"))).andExpect(status().isForbidden());
        mockMvc
            .perform(
                post("/api/holidays").with(user("ketoan").authorities(() -> "ROLE_KE_TOAN")).contentType(MediaType.APPLICATION_JSON).content("{\"day\":\"2026-01-01\",\"name\":\"Tết\"}")
            )
            .andExpect(status().isForbidden());
    }
}
