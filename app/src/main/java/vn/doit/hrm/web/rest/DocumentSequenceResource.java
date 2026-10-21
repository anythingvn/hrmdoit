package vn.doit.hrm.web.rest;

import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.domain.enumeration.DocumentType;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.DocumentNumberService;
import vn.doit.hrm.service.dto.DocumentSequenceDTO;

/** Dãy số văn bản theo loại và năm: Quản trị và Nhân sự xem; chỉ Quản trị đặt số bắt đầu. */
@RestController
@RequestMapping("/api/admin/document-sequences")
public class DocumentSequenceResource {

    private final DocumentNumberService numberService;
    private final Clock clock;

    public DocumentSequenceResource(DocumentNumberService numberService, Clock clock) {
        this.numberService = numberService;
        this.clock = clock;
    }

    public record NextValueVM(@NotNull Integer nextValue) {}

    /** {@code GET /admin/document-sequences?year=} : mọi loại văn bản của năm (mặc định năm nay). */
    @GetMapping("")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')")
    public List<DocumentSequenceDTO> list(@RequestParam(required = false) Integer year) {
        return numberService.list(year != null ? year : LocalDate.now(clock).getYear());
    }

    /** {@code PUT /admin/document-sequences/{type}/{year}} : đặt số sẽ cấp tiếp. */
    @PutMapping("/{type}/{year}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public ResponseEntity<DocumentSequenceDTO> setNextValue(
        @PathVariable DocumentType type,
        @PathVariable int year,
        @jakarta.validation.Valid @RequestBody NextValueVM body
    ) {
        return ResponseEntity.ok(numberService.setNextValue(type, year, body.nextValue()));
    }
}
