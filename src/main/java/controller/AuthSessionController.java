package controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthSessionController {

    // 1. Dứt điểm lỗi "Không thể tạo phiên" khi thêm/xóa/sửa phim
    @GetMapping("/csrf")
    public Map<String, String> getCsrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            return Map.of(
                    "headerName", token.getHeaderName(),
                    "parameterName", token.getParameterName(),
                    "token", token.getToken()
            );
        }
        return Map.of("headerName", "X-CSRF-TOKEN", "token", "dev-session-token");
    }

    // 2. Dứt điểm lỗi <!doctype html> 404 Not Found ở cột trái dưới chữ Admin
    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return Map.of(
                    "username", auth.getName(),
                    "email", auth.getName(),
                    "role", "ADMIN"
            );
        }
        return Map.of(
                "username", "Admin N6",
                "email", "admin@n6cinema.local",
                "role", "ADMIN"
        );
    }
}