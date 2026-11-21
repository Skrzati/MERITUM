package pl.gov.kprm.oceny.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gov.kprm.oceny.api.Dtos.UserDto;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.repository.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;

    @GetMapping("/demo-users")
    public List<UserDto> demoUsers() {
        return users.findAllByOrderByIdAsc().stream().map(UserDto::of).toList();
    }

    @GetMapping("/me")
    public UserDto me(@CurrentUser User user) {
        return UserDto.of(user);
    }
}
