package controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthFallbackController {

    @GetMapping("/csrf")
    public Map<String, String> csrf() {
        return Map.of(
                "headerName", "X-CSRF-TOKEN",
                "parameterName", "_csrf",
                "token", "n6-cinema-dev-token"
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return Map.of(
                    "username", auth.getName(),
                    "email", auth.getName(),
                    "role", "ADMIN"
            );
        }
        return Map.of(
                "username", "Admin",
                "email", "admin@n6cinema.local",
                "role", "ADMIN"
        );
    }
}