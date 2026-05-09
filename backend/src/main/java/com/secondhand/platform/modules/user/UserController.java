package com.secondhand.platform.modules.user;

import com.secondhand.platform.modules.user.application.UserApplicationService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserApplicationService userApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public UserController(UserApplicationService userApplicationService, CurrentUserResolver currentUserResolver) {
        this.userApplicationService = userApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/me")
    public Result<UserProfileResponse> me(HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(userApplicationService.currentUserProfile(userId));
    }

    @GetMapping("/{userId}/profile")
    public Result<UserProfileResponse> publicProfile(@org.springframework.web.bind.annotation.PathVariable Long userId) {
        return Result.ok(userApplicationService.publicProfile(userId));
    }
}
