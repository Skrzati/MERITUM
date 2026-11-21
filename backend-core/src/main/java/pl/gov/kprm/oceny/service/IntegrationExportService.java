package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.*;
import pl.gov.kprm.oceny.export.EzdZip;
import pl.gov.kprm.oceny.export.IcsCalendarRenderer;
import pl.gov.kprm.oceny.export.IcsCalendarRenderer.Attendee;
import pl.gov.kprm.oceny.export.IcsCalendarRenderer.Invitation;
import pl.gov.kprm.oceny.repository.OfficeRepository;
import pl.gov.kprm.oceny.repository.SchemaVersionRepository;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static pl.gov.kprm.oceny.service.Access.notFound;

/** Integracje z systemami urzędu: zaproszenie kalendarzowe (.ics) i paczka metadanych do EZD RP (KRI). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IntegrationExportService {
    private static final LocalTime DEFAULT_INTERVIEW_TIME = LocalTime.of(10, 0);
    private static final int INTERVIEW_MINUTES = 60;
    private static final String UID_DOMAIN = "@meritum.kprm.gov.pl";
    private static final String DOCUMENT_TYPE = "ARKUSZ_OCENY_OKRESOWEJ";
    private static final String PDF_FORMAT = "application/pdf (PDF/A-2b)";
    private static final String ACCESS_RESTRICTION = "OGRANICZONY — dane osobowe, akta osobowe pracownika";
    private static final String LEGAL_BASIS = "art. 81–83 ustawy o służbie cywilnej; rozporządzenie PRM z 4.04.2016 (Dz. U. poz. 470)";

    private final CycleWorkflowService workflow;
    private final UserRepository users;
    private final OfficeRepository offices;
    private final SchemaVersionRepository schemas;
    private final Clock clock;
    private final PdfExportService pdfExport;
    private final AuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public record Party(Long id, String name, String role) {
    }

    /** Metadane dokumentu elektronicznego wg instrukcji kancelaryjnej (identyfikator, twórca, odbiorca, typ, format…). */
    public record EzdPackage(String caseId, String title, String documentType, SheetType sheetType, String office,
                             List<Party> parties, CycleStatus status, LocalDate evaluatedOn, LocalDate acknowledgedAt,
                             SignatureMethod signatureMethod, String format, String access, String legalBasis,
                             String pdfEndpoint, String schemaVersion, OffsetDateTime generatedAt) {
    }

    public String interviewIcs(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        User employee = user(cycle.getEmployeeId());
        User evaluator = user(cycle.getEvaluatorId());
        boolean initial = cycle.getStatus() == CycleStatus.DRAFT_CRITERIA;
        LocalDate date = initial
                ? firstNonNull(cycle.getCriteriaInterviewDate(), workflow.criteriaDeadline(cycle))
                : firstNonNull(cycle.getEvaluationInterviewDate(), cycle.getDueDate());
        String summary = (initial ? "Rozmowa rozpoczynająca" : "Rozmowa oceniająca") + " — ocena okresowa: "
                + employee.getFullName();
        String description = initial
                ? "Rozmowa rozpoczynająca okres oceny (§ 5 ust. 1 rozporządzenia PRM z 4.04.2016): omówienie zadań, "
                + "wybór kryteriów i oczekiwań, poinformowanie o zasadach oceny."
                : "Rozmowa oceniająca (§ 10 rozporządzenia PRM z 4.04.2016): podsumowanie okresu oceny, "
                + "omówienie wyników i działań rozwojowych (IPRZ).";
        return IcsCalendarRenderer.render(new Invitation(
                "cycle-" + cycleId + "-" + (initial ? "initial" : "evaluation") + UID_DOMAIN,
                clock.instant(), date, DEFAULT_INTERVIEW_TIME, INTERVIEW_MINUTES, clock.getZone(), summary,
                description + " Cykl nr " + cycleId + ".", location(cycle),
                attendee(evaluator), List.of(attendee(employee), attendee(evaluator))));
    }

    public EzdPackage ezdPackage(Long cycleId, User user) {
        EvaluationCycle cycle = workflow.get(cycleId, user);
        User employee = user(cycle.getEmployeeId());
        User evaluator = user(cycle.getEvaluatorId());
        String schemaVersion = schemas.findById(cycle.getSchemaVersionId())
                .map(SchemaVersion::getVersionCode).orElse(null);
        return new EzdPackage(
                "MERITUM/" + cycle.getOfficeId() + "/" + cycle.getStartDate().getYear() + "/" + cycleId,
                "Arkusz oceny okresowej — " + employee.getFullName(),
                DOCUMENT_TYPE, cycle.getSheetType(), office(cycle).getName(),
                List.of(new Party(evaluator.getId(), evaluator.getFullName(), "OCENIAJACY"),
                        new Party(employee.getId(), employee.getFullName(), "OCENIANY")),
                cycle.getStatus(), cycle.getEvaluatedOn(), cycle.getAcknowledgedAt(), cycle.getSignatureMethod(),
                PDF_FORMAT, ACCESS_RESTRICTION, LEGAL_BASIS,
                "/api/cycles/" + cycleId + "/pdf", schemaVersion, OffsetDateTime.now(clock));
    }

    /** Jednoklikowa paczka EZD RP: arkusz PDF/A + metadane (audyt EZD_ZIP_EXPORTED). */
    @Transactional
    public byte[] ezdZip(Long cycleId, User user) {
        EzdPackage metadata = ezdPackage(cycleId, user);
        byte[] pdf = pdfExport.export(cycleId, user);
        byte[] zip;
        try {
            zip = EzdZip.build(cycleId, pdf, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadata));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Nie można zapisać metadanych EZD", e);
        }
        EvaluationCycle cycle = workflow.get(cycleId, user);
        audit.log(user, cycle.getOfficeId(), "EZD_ZIP_EXPORTED", AuditService.CYCLE, cycleId,
                java.util.Map.of("caseId", metadata.caseId(), "bytes", zip.length));
        return zip;
    }

    private String location(EvaluationCycle cycle) {
        if (cycle.getPlace() != null && !cycle.getPlace().isBlank()) {
            return cycle.getPlace();
        }
        Office office = office(cycle);
        return office.getCity() == null ? office.getName() : office.getName() + ", " + office.getCity();
    }

    private Office office(EvaluationCycle cycle) {
        return offices.findById(cycle.getOfficeId()).orElseThrow(() -> notFound("Urząd", cycle.getOfficeId()));
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> notFound("Użytkownik", id));
    }

    private static Attendee attendee(User u) {
        return new Attendee(u.getFullName(), u.getEmail());
    }

    private static LocalDate firstNonNull(LocalDate preferred, LocalDate fallback) {
        return preferred != null ? preferred : fallback;
    }
}
