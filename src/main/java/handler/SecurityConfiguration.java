package handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;

import java.util.Map;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            ObjectMapper json,
            SecurityContextRepository contexts,
            CsrfTokenRepository csrf,
            repository.AccountRepository accounts)
            throws Exception {
        return http.securityContext(c -> c.securityContextRepository(contexts))
                .addFilterAfter(new SessionCredentialFilter(accounts),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .csrf(c -> c.csrfTokenRepository(csrf))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/api/auth/csrf",
                                                "/api/auth/login",
                                                "/api/auth/register")
                                        .permitAll()
                                        .requestMatchers("/api/auth/me", "/api/auth/logout")
                                        .authenticated()
                                        .requestMatchers("/api/auth/change-password")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/api/commerce/catalog", "/api/commerce/shows/*/seats")
                                        .permitAll()
                                        .requestMatchers("/api/commerce/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/commerce/**")
                                        .authenticated()
                                        .requestMatchers("/api/auth/profile", "/api/bookings/mine")
                                        .hasRole("CUSTOMER")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/customers/policies",
                                                "/api/customers/student-id/check",
                                                "/api/movies",
                                                "/api/movies/**",
                                                "/api/showtimes",
                                                "/api/showtimes/**",
                                                "/api/rooms",
                                                "/api/rooms/**",
                                                "/api/bookings/showtimes/*/seats")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/bookings/quote")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/api/bookings")
                                        .hasRole("CUSTOMER")
                                        .requestMatchers(HttpMethod.POST, "/api/bookings/*/cancel")
                                        .authenticated()
                                        .requestMatchers(
                                                "/api/**",
                                                "/admin.html",
                                                "/movies.html",
                                                "/showtimes.html",
                                                "/rooms.html",
                                                "/customers.html")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .permitAll())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) -> {
                                                    if (!req.getRequestURI().startsWith("/api/")) {
                                                        res.sendRedirect("/welcome.html?login=1");
                                                        return;
                                                    }
                                                    res.setStatus(401);
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getWriter(),
                                                            Map.of(
                                                                    "message",
                                                                    "Vui lòng đăng nhập."));
                                                })
                                        .accessDeniedHandler(
                                                (req, res, ex) -> {
                                                    if (!req.getRequestURI().startsWith("/api/")) {
                                                        res.sendRedirect("/welcome.html");
                                                        return;
                                                    }
                                                    res.setStatus(403);
                                                    res.setContentType(
                                                            "application/json;charset=UTF-8");
                                                    json.writeValue(
                                                            res.getWriter(),
                                                            Map.of(
                                                                    "code",
                                                                            ex
                                                                                            instanceof
                                                                                            CsrfException
                                                                                    ? "CSRF_INVALID"
                                                                                    : "FORBIDDEN",
                                                                    "message",
                                                                            ex
                                                                                            instanceof
                                                                                            CsrfException
                                                                                    ? "Phiên bảo"
                                                                                          + " mật đã"
                                                                                          + " hết hạn."
                                                                                          + " Vui lòng"
                                                                                          + " thử lại."
                                                                                    : "Bạn không có"
                                                                                          + " quyền"
                                                                                          + " thực"
                                                                                          + " hiện"
                                                                                          + " thao"
                                                                                          + " tác này."));
                                                }))
                .logout(l -> l.disable())
                .formLogin(l -> l.disable())
                .httpBasic(l -> l.disable())
                .build();
    }
}
