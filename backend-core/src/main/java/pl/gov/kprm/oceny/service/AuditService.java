package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gov.kprm.oceny.domain.AuditLog;
import pl.gov.kprm.oceny.domain.Role;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.AuditLogRepository;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {
    public static final String CYCLE = "EVALUATION_CYCLE";
    public static final String USER = "USER";
    public static final String SCHEMA = "SCHEMA_VERSION";
    public static final String AI = "AI_INTERACTION";
    private static final int MAX_LIMIT = 200;

    private final AuditLogRepository auditLogs;
    private final ObjectMapper json;

    public void log(User actor, Long officeId, String action, String entityType, Long entityId, Map<String, ?> payload) {
        AuditLog entry = new AuditLog();
        entry.setOfficeId(officeId);
        entry.setActorId(actor.getId());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setPayloadJson(json.valueToTree(payload));
        auditLogs.save(entry);
    }

    /** Ostatnie zdarzenia: HR i DG — własny urząd, KPRM_ADMIN — wszystkie urzędy. */
    @Transactional(readOnly = true)
    public List<AuditLog> recent(User user, int limit, Integer pageNumber, Integer size) {
        Access.requireRole(user, Role.HR, Role.DG, Role.KPRM_ADMIN);
        PageRequest page = Paging.of(pageNumber, size, Sort.unsorted())
                .orElseGet(() -> PageRequest.of(0, Math.max(1, Math.min(limit, MAX_LIMIT))));
        return user.getRole() == Role.KPRM_ADMIN
                ? auditLogs.findAllByOrderByCreatedAtDescIdDesc(page)
                : auditLogs.findByOfficeIdOrderByCreatedAtDescIdDesc(user.getOfficeId(), page);
    }
}
