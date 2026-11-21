package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.api.Dtos.GdprExportDto;
import pl.gov.kprm.oceny.api.Dtos.UserDto;
import pl.gov.kprm.oceny.api.Requests.AbsenceRequest;
import pl.gov.kprm.oceny.api.Requests.HandoverRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.HrService;
import pl.gov.kprm.oceny.service.HrService.HandoverResult;
import pl.gov.kprm.oceny.service.HrService.ImportResult;
import pl.gov.kprm.oceny.service.OverdueService;
import pl.gov.kprm.oceny.service.OverdueService.OverdueItem;
import pl.gov.kprm.oceny.service.ReportExportService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {
    private final HrService hr;
    private final CycleMapper mapper;
    private final OverdueService overdue;
    private final ReportExportService reports;

    /** Aktywne cykle po terminie (HR, DG) — malejąco wg liczby dni opóźnienia, z poziomem eskalacji. */
    @GetMapping("/overdue")
    public List<OverdueItem> overdue(@CurrentUser User user) {
        return overdue.overdue(user);
    }

    @GetMapping("/employees")
    public List<UserDto> employees(@CurrentUser User user,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "fullName") String sort,
                                   @RequestParam(defaultValue = "ASC") Sort.Direction dir) {
        return hr.employees(user, q, sort, dir).stream().map(UserDto::of).toList();
    }

    /** Zwraca aktywne cykle pracownika z przeliczonym due_date. */
    @PostMapping("/absences")
    public List<CycleDto> registerAbsence(@CurrentUser User user, @Valid @RequestBody AbsenceRequest req) {
        return hr.registerAbsence(user, req.userId(), req.startDate(), req.endDate(), req.reason())
                .stream().map(mapper::toDto).toList();
    }

    @PostMapping("/handover")
    public HandoverResult handover(@CurrentUser User user, @Valid @RequestBody HandoverRequest req) {
        return hr.handover(user, req.employeeId(), req.newEvaluatorId());
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@CurrentUser User user, @RequestParam("file") MultipartFile file)
            throws IOException {
        return hr.importCsv(user, file.getInputStream());
    }

    @GetMapping("/employees/{id}/gdpr-export")
    public GdprExportDto gdprExport(@CurrentUser User user, @PathVariable Long id) {
        return GdprExportDto.of(hr.gdprExport(user, id));
    }

    /** Zanonimizowany raport CSV (UTF-8 z BOM dla Excela): cykle wg statusów, wyników i zaległości. */
    @GetMapping(value = "/reports/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportReport(@CurrentUser User user) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("raport-ocen.csv").build().toString())
                .body(reports.cyclesReportCsv(user).getBytes(StandardCharsets.UTF_8));
    }
}
