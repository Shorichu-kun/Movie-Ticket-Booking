package handler;

import dto.CustomerRequest;

import jakarta.servlet.http.*;

import model.Account;

import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

import service.AccountService;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthHandler {
    private final AccountService accounts;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;

    public AuthHandler(
            AccountService a, SecurityContextRepository contexts, CsrfTokenRepository csrf) {
        this.accounts = a;
        this.contexts = contexts;
        this.csrf = csrf;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public AccountService.UserView me(Authentication authentication) {
        return accounts.view(accounts.require(authentication));
    }

    @PostMapping("/register")
    public ResponseEntity<AccountService.UserView> register(
            @RequestBody AccountService.Registration registration,
            HttpServletRequest req,
            HttpServletResponse res) {
        Account a = accounts.register(registration);
        authenticate(a, req, res);
        return ResponseEntity.status(201).body(accounts.view(a));
    }

    @PostMapping("/login")
    public AccountService.UserView login(
            @RequestBody Login login, HttpServletRequest req, HttpServletResponse res) {
        Account a = accounts.login(login.email(), login.password());
        authenticate(a, req, res);
        return accounts.view(a);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest req, HttpServletResponse res) {
        csrf.saveToken(null, req, res);
        SecurityContextHolder.clearContext();
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("message", "Đã đăng xuất.");
    }

    @PutMapping("/profile")
    public AccountService.UserView profile(
            Authentication auth, @RequestBody ProfileUpdate request) {
        return accounts.profile(auth, new CustomerRequest(request.fullName(), request.phone(),
                request.email(), request.customerType(), request.studentId()), request.currentPassword());
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(Authentication auth, @RequestBody PasswordChange change,
                                             HttpServletRequest request) {
        accounts.changePassword(auth, change.currentPassword(), change.newPassword());
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("message", "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }

    private void authenticate(Account a, HttpServletRequest req, HttpServletResponse res) {
        if (req.getSession(false) != null) req.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        a.id(), null, List.of(new SimpleGrantedAuthority("ROLE_" + a.role()))));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, req, res);
        req.getSession().setAttribute(SessionCredentialFilter.CREDENTIAL_VERSION, a.passwordHash());
        csrf.saveToken(null, req, res);
    }

    public record Login(String email, String password) {}
    public record PasswordChange(String currentPassword, String newPassword) {}
    public record ProfileUpdate(String fullName, String phone, String email,
                                model.CustomerType customerType, String studentId,
                                String currentPassword) {}
}
