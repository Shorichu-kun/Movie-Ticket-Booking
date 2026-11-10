package security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import repository.AccountRepository;

public class SessionCredentialFilter extends OncePerRequestFilter {
  public static final String CREDENTIAL_VERSION = "n6.credentialVersion";
  private final AccountRepository accounts;

  public SessionCredentialFilter(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    HttpSession session = request.getSession(false);
    if (session != null && auth != null && auth.isAuthenticated()) {
      Object version = session.getAttribute(CREDENTIAL_VERSION);
      boolean valid =
          accounts.findAll().stream()
              .anyMatch(
                  a -> a.id().equals(auth.getName()) && Objects.equals(a.passwordHash(), version));
      if (!valid) {
        session.invalidate();
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
