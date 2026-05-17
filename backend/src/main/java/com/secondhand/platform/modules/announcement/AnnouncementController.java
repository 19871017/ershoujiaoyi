package com.secondhand.platform.modules.announcement;

import com.secondhand.platform.shared.kernel.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final AnnouncementApplicationService announcementApplicationService;

    public AnnouncementController(AnnouncementApplicationService announcementApplicationService) {
        this.announcementApplicationService = announcementApplicationService;
    }

    @GetMapping("/ticker")
    public Result<AnnouncementTickerResponse> ticker() {
        return Result.ok(announcementApplicationService.getTicker());
    }
}
