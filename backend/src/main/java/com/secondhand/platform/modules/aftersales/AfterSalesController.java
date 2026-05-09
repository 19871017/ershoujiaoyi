package com.secondhand.platform.modules.aftersales;

import com.secondhand.platform.modules.aftersales.application.AfterSalesApplicationService;
import com.secondhand.platform.modules.aftersales.application.CreateAfterSalesRequest;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/after-sales")
public class AfterSalesController {
    private final AfterSalesApplicationService afterSalesApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public AfterSalesController(AfterSalesApplicationService afterSalesApplicationService, CurrentUserResolver currentUserResolver) {
        this.afterSalesApplicationService = afterSalesApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping
    public Result<AfterSalesResponse> create(@RequestBody CreateAfterSalesRequest request, HttpServletRequest httpRequest) {
        long userId = currentUserResolver.resolve(httpRequest);
        return Result.ok(afterSalesApplicationService.create(userId, request));
    }

    @GetMapping("/{afterSalesNo}")
    public Result<AfterSalesResponse> detail(@PathVariable String afterSalesNo, HttpServletRequest httpRequest) {
        long userId = currentUserResolver.resolve(httpRequest);
        return Result.ok(afterSalesApplicationService.detail(afterSalesNo, userId));
    }
}
