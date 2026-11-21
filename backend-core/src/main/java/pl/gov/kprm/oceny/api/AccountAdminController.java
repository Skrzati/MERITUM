package pl.gov.kprm.oceny.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.gov.kprm.oceny.api.Dtos.AccountDto;
import pl.gov.kprm.oceny.api.Dtos.OfficeDto;
import pl.gov.kprm.oceny.api.Requests.CreateAccountRequest;
import pl.gov.kprm.oceny.api.Requests.SetPasswordRequest;
import pl.gov.kprm.oceny.auth.CurrentUser;
import pl.gov.kprm.oceny.domain.User;
import pl.gov.kprm.oceny.service.HrService.ImportResult;
import pl.gov.kprm.oceny.service.LocalAccountService;
import pl.gov.kprm.oceny.service.LocalAccountService.NewAccount;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Tryb Mały Urząd w /admin: konta lokalne, import CSV, włączanie logowania hasłem (KPRM_ADMIN; kadry — własny urząd). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AccountAdminController {
    private final LocalAccountService accounts;

    @GetMapping("/offices")
    public List<OfficeDto> offices(@CurrentUser User user) {
        return accounts.offices(user).stream().map(OfficeDto::of).toList();
    }

    @PatchMapping("/offices/{id}/local-auth")
    public OfficeDto setLocalAuth(@CurrentUser User user, @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return OfficeDto.of(accounts.setLocalAuth(user, id, Boolean.TRUE.equals(body.get("enabled"))));
    }

    @GetMapping("/accounts")
    public List<AccountDto> list(@CurrentUser User user, @RequestParam(required = false) Long officeId) {
        return accounts.list(user, officeId).stream().map(AccountDto::of).toList();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto create(@CurrentUser User user, @Valid @RequestBody CreateAccountRequest req) {
        return AccountDto.of(accounts.create(user, new NewAccount(req.officeId(), req.firstName(), req.lastName(),
                req.email(), req.role(), req.employmentStatus(), req.supervisorId(), req.department(),
                req.positionTitle(), req.password())));
    }

    @PostMapping("/accounts/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(@CurrentUser User user, @PathVariable Long id, @Valid @RequestBody SetPasswordRequest req) {
        accounts.setPassword(user, id, req.password());
    }

    @PostMapping(value = "/accounts/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importCsv(@CurrentUser User user, @RequestParam(required = false) Long officeId,
                                  @RequestParam("file") MultipartFile file) throws IOException {
        return accounts.importCsv(user, officeId, file.getInputStream());
    }
}
