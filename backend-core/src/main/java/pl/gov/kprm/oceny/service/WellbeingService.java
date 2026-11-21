package pl.gov.kprm.oceny.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.domain.WellbeingSurvey;
import pl.gov.kprm.oceny.repository.WellbeingSurveyRepository;

/**
 * Dobrowolna ankieta dobrostanu. Celowo bez wpisu w audit_logs i bez powiązania z cyklem oceny — odpowiedzi
 * trafiają wyłącznie do zagregowanych statystyk (k-anonimowość) jako sygnał organizacyjny.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class WellbeingService {
    private final WellbeingSurveyRepository surveys;

    public void submit(User user, int workload, int energy, int support, boolean wantsConversation) {
        WellbeingSurvey survey = new WellbeingSurvey();
        survey.setUserId(user.getId());
        survey.setOfficeId(user.getOfficeId());
        survey.setWorkload(workload);
        survey.setEnergy(energy);
        survey.setSupport(support);
        survey.setWantsConversation(wantsConversation);
        surveys.save(survey);
    }
}
