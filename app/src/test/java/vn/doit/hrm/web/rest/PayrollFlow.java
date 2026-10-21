package vn.doit.hrm.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Các bước import bảng lương mẫu dùng chung cho IT: tải file, gắn đủ 7 dòng, xác nhận cảnh báo. */
final class PayrollFlow {

    static final RequestPostProcessor KE_TOAN = user("ketoan-it").authorities(() -> "ROLE_KE_TOAN");
    static final RequestPostProcessor ADMIN = user("admin-it").authorities(() -> "ROLE_ADMIN");

    private PayrollFlow() {}

    static Integer upload(MockMvc mvc, String period) throws Exception {
        return JsonPath.read(
            mvc
                .perform(
                    multipart("/api/payroll/imports")
                        .file(PayrollImportResourceIT.file("bang-luong.xlsx", PayrollImportResourceIT.fixture()))
                        .param("period", period)
                        .with(KE_TOAN)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id"
        );
    }

    /**
     * Bản nháp sẵn sàng xác nhận: dòng 18/19 → DOIT2515/2516, dòng 21/22/23 → hồ sơ {@code extra} ("TT2601", "HDDV9001",
     * "HDDV9002"), đã xác nhận cảnh báo.
     */
    static Integer readyDraft(MockMvc mvc, String period, Map<String, Long> ids) throws Exception {
        Integer id = upload(mvc, period);
        map(mvc, id, 18, ids.get("DOIT2515"));
        map(mvc, id, 19, ids.get("DOIT2516"));
        map(mvc, id, 21, ids.get("TT2601"));
        map(mvc, id, 22, ids.get("HDDV9001"));
        map(mvc, id, 23, ids.get("HDDV9002"));
        mvc.perform(post("/api/payroll/imports/{id}/acknowledge-warnings", id).with(KE_TOAN)).andExpect(status().isOk());
        return id;
    }

    static void map(MockMvc mvc, Integer importId, int row, Long employeeId) throws Exception {
        mvc
            .perform(
                put("/api/payroll/imports/{id}/rows/{row}/employee", importId, row)
                    .with(KE_TOAN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"employeeId\":" + employeeId + "}")
            )
            .andExpect(status().isOk());
    }
}
