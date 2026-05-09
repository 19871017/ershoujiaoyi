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
    public Result<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(authApplicationService.login(request));
    }
}
