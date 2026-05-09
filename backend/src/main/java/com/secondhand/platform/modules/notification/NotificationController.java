package com.secondhand.platform.modules.notification;

import com.secondhand.platform.modules.notification.application.NotificationApplicationService;
import com.secondhand.platform.modules.notification.application.NotificationItemResponse;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationApplicationService notificationApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public NotificationController(NotificationApplicationService notificationApplicationService, CurrentUserResolver currentUserResolver) {
        this.notificationApplicationService = notificationApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public Result<List<NotificationItemResponse>> list(@RequestParam(defaultValue = "ALL") String type,
                                                       @RequestParam(defaultValue = "20") int limit,
                                                       HttpServletRequest request) {
        return Result.ok(notificationApplicationService.listNotifications(currentUserResolver.resolve(request), type, limit));
    }

    @PostMapping("/{notificationNo}/read")
    public Result<NotificationItemResponse> markRead(@PathVariable String notificationNo, HttpServletRequest request) {
        return Result.ok(notificationApplicationService.markRead(currentUserResolver.resolve(request), notificationNo));
    }
}
