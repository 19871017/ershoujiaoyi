package com.secondhand.platform.modules.home;

import com.secondhand.platform.shared.kernel.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeBannerApplicationService homeBannerApplicationService;

    public HomeController(HomeBannerApplicationService homeBannerApplicationService) {
        this.homeBannerApplicationService = homeBannerApplicationService;
    }

    @GetMapping("/banners")
    public Result<List<HomeBannerResponse>> banners() {
        return Result.ok(homeBannerApplicationService.listEnabled());
    }
}
