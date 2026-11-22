package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.ManagementReportService;
import pl.gov.kprm.oceny.service.ManagementReportService.Calibration;
import pl.gov.kprm.oceny.service.StatsService;
import pl.gov.kprm.oceny.service.StatsService.Summary;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService stats;
    private final ManagementReportService management;

    @GetMapping("/summary")
    public Summary summary(@CurrentUser User user) {
        return stats.summary(user);
    }

    /** Kalibracja ocen per komórka: komórki z mniej niż k ocenami pominięte, liczności 1..k-1 ukryte. */
    @GetMapping("/calibration")
    public Calibration calibration(@CurrentUser User user, @RequestParam(required = false) Integer year) {
        return management.calibration(user, year);
    }
}
