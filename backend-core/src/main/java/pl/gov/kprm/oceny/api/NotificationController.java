package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.NotificationService;
import pl.gov.kprm.oceny.service.NotificationService.Notification;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    public List<Notification> list(@CurrentUser User user) {
        return notifications.forUser(user);
    }
}
