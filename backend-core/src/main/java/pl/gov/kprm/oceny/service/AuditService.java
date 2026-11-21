package pl.gov.kprm.oceny.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.gov.kprm.oceny.domain.AuditLog;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.AuditLogRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {
    public static final String CYCLE = "EVALUATION_CYCLE";
    public static final String USER = "USER";
    public static final String SCHEMA = "SCHEMA_VERSION";

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
}
