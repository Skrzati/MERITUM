package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.gov.kprm.oceny.api.Dtos.CycleDto;
import pl.gov.kprm.oceny.api.Dtos.GdprExportDto;
import pl.gov.kprm.oceny.api.Dtos.HistoryDto;
import pl.gov.kprm.oceny.api.Dtos.UserDto;
import pl.gov.kprm.oceny.api.Requests.AbsenceRequest;
import pl.gov.kprm.oceny.api.Requests.HandoverRequest;
import pl.gov.kprm.oceny.api.Requests.HistoryRequest;
import pl.gov.kprm.oceny.api.Requests.UpdateEmployeeRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.HrAlertService;
import pl.gov.kprm.oceny.service.HrAlertService.HrAlert;
import pl.gov.kprm.oceny.service.HrService;
import pl.gov.kprm.oceny.service.HrService.EmployeeUpdate;
import pl.gov.kprm.oceny.service.HrService.HandoverResult;
import pl.gov.kprm.oceny.service.HrService.RemovalOutcome;
import pl.gov.kprm.oceny.service.HrService.ImportResult;
import pl.gov.kprm.oceny.service.OverdueService;
import pl.gov.kprm.oceny.service.OverdueService.OverdueItem;
import pl.gov.kprm.oceny.service.ReportExportService;
import pl.gov.kprm.oceny.service.WorkingNotesRetentionService;
import pl.gov.kprm.oceny.service.WorkingNotesRetentionService.PurgeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
public class HrController {
    private final HrService hr;
    private final CycleMapper mapper;
    private final OverdueService overdue;
    private final ReportExportService reports;
    private final HrAlertService alerts;
    private final WorkingNotesRetentionService retention;

    /** Aktywne cykle po terminie (HR, DG) — malejąco wg liczby dni opóźnienia, z poziomem eskalacji. */
    @GetMapping("/overdue")
    public List<OverdueItem> overdue(@CurrentUser User user) {
        return overdue.overdue(user);
    }

    /** Alerty kadrowe z historii ocen: art. 71 ust. 1 pkt 1 (krytyczne), pracownik SC (informacyjne), art. 89 ust. 2. */
    @GetMapping("/alerts")
    public List<HrAlert> alerts(@CurrentUser User user) {
        return alerts.alerts(user);
    }

    /** Czyszczenie danych pomocniczych zamkniętych cykli urzędu (notatki robocze, ankiety); arkusze pozostają. */
    @PostMapping("/retention/purge-working-notes")
    public PurgeResult purgeWorkingNotes(@CurrentUser User user) {
        return retention.purgeForOffice(user);
    }

    @GetMapping("/employees")
    public List<UserDto> employees(@CurrentUser User user,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "fullName") String sort,
                                   @RequestParam(defaultValue = "ASC") Sort.Direction dir,
                                   @RequestParam(required = false) Integer page,
                                   @RequestParam(required = false) Integer size) {
        return hr.employees(user, q, sort, dir, page, size).stream().map(UserDto::of).toList();
    }

    /** Zwraca aktywne cykle pracownika z przeliczonym due_date. */
    @PostMapping("/absences")
    public List<CycleDto> registerAbsence(@CurrentUser User user, @Valid @RequestBody AbsenceRequest req) {
        return mapper.toDtos(hr.registerAbsence(user, req.userId(), req.startDate(), req.endDate(), req.reason()));
    }

    @PatchMapping("/employees/{id}")
    public UserDto updateEmployee(@CurrentUser User user, @PathVariable Long id,
                                  @Valid @RequestBody UpdateEmployeeRequest req) {
        return UserDto.of(hr.updateEmployee(user, id, new EmployeeUpdate(req.firstName(), req.lastName(), req.department(),
                req.positionTitle(), req.role(), req.civilServant(), req.employmentStatus(), req.civilServiceGrade(), req.appointmentYear(),
                req.lastGradeDate(), req.supervisorId(), req.jobDescription(), req.employmentStartDate())));
    }

    /** Usunięcie danych pracownika: DELETED (brak śladów w procesie) albo ANONYMIZED (cykle/historia/audyt zachowane). */
    @DeleteMapping("/employees/{id}")
    public Map<String, RemovalOutcome> removeEmployee(@CurrentUser User user, @PathVariable Long id) {
        return Map.of("outcome", hr.removeEmployee(user, id));
    }

    @GetMapping("/employees/{id}/history")
    public List<HistoryDto> history(@CurrentUser User user, @PathVariable Long id) {
        return hr.history(user, id).stream().map(HistoryDto::of).toList();
    }

    /** Ocena sprzed wdrożenia systemu (source = HISTORICAL). */
    @PostMapping("/employees/{id}/history")
    @ResponseStatus(HttpStatus.CREATED)
    public HistoryDto addHistory(@CurrentUser User user, @PathVariable Long id, @Valid @RequestBody HistoryRequest req) {
        return HistoryDto.of(hr.addHistory(user, id, req.outcome(), req.level(), req.evaluatedOn()));
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
