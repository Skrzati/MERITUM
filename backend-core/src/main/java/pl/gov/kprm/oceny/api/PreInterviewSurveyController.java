package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.gov.kprm.oceny.api.Requests.SurveyAnswersRequest;
import pl.gov.kprm.oceny.api.Requests.SurveyQuestionRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.PreInterviewSurveyService;
import pl.gov.kprm.oceny.service.PreInterviewSurveyService.SurveyView;

/** Ankieta przygotowawcza przed rozmową — niezależna od anonimowej ankiety dobrostanu (/api/wellbeing). */
@RestController
@RequestMapping("/api/cycles/{id}/pre-interview-survey")
@RequiredArgsConstructor
public class PreInterviewSurveyController {
    private final PreInterviewSurveyService surveys;

    @GetMapping
    public SurveyView get(@PathVariable Long id, @CurrentUser User user) {
        return surveys.view(id, user);
    }

    @PostMapping("/questions")
    public SurveyView addQuestion(@PathVariable Long id, @CurrentUser User user,
                                  @Valid @RequestBody SurveyQuestionRequest req) {
        return surveys.addQuestion(id, user, req.text());
    }

    @DeleteMapping("/questions/{questionId}")
    public SurveyView removeQuestion(@PathVariable Long id, @PathVariable String questionId, @CurrentUser User user) {
        return surveys.removeQuestion(id, user, questionId);
    }

    @PutMapping("/answers")
    public SurveyView saveAnswers(@PathVariable Long id, @CurrentUser User user,
                                  @Valid @RequestBody SurveyAnswersRequest req) {
        return surveys.saveAnswers(id, user, req.answers());
    }

    /** „Wyślij przełożonemu”. */
    @PostMapping("/send")
    public SurveyView send(@PathVariable Long id, @CurrentUser User user) {
        return surveys.send(id, user);
    }
}
