package com.secondhand.platform.modules.auth;

import com.secondhand.platform.modules.auth.application.AuthApplicationService;
import com.secondhand.platform.shared.kernel.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public Result<AuthTokenResponse> login(@RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        return Result.ok(authApplicationService.login(request, resolveClientIp(httpRequest)));
    }

    @PostMapping("/register")
    public Result<AuthTokenResponse> register(@RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        return Result.ok(authApplicationService.register(request, resolveClientIp(httpRequest)));
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }
        return remoteAddr.trim();
    }
}
