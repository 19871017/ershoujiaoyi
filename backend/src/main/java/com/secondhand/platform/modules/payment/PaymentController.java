package com.secondhand.platform.modules.payment;

import com.secondhand.platform.modules.payment.application.CreateRechargeRequest;
import com.secondhand.platform.modules.payment.application.PaymentApplicationService;
import com.secondhand.platform.modules.payment.application.RechargeResponse;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentApplicationService paymentApplicationService;
    private final CurrentUserResolver currentUserResolver;
    private final Environment environment;

    public PaymentController(PaymentApplicationService paymentApplicationService, CurrentUserResolver currentUserResolver, Environment environment) {
        this.paymentApplicationService = paymentApplicationService;
        this.currentUserResolver = currentUserResolver;
        this.environment = environment;
    }

    @PostMapping("/recharge")
    public Result<RechargeResponse> createRecharge(@RequestBody CreateRechargeRequest request, HttpServletRequest httpRequest) {
        return Result.ok(paymentApplicationService.createRecharge(currentUserResolver.resolve(httpRequest), request));
    }

    @PostMapping("/recharge/simulate-success")
    public Result<RechargeResponse> simulateRechargeSuccess(
            @RequestBody SimulateRechargeRequest request,
            @RequestHeader(value = "X-Dev-Mode", required = false) String devMode,
            HttpServletRequest httpRequest) {
        requireDevelopmentProfile();
        if (!"enabled".equals(devMode)) {
            throw new IllegalArgumentException("dev simulate recharge endpoint disabled");
        }
        if (request == null || request.getRechargeNo() == null || request.getRechargeNo().isBlank()) {
            throw new IllegalArgumentException("rechargeNo required");
        }
        // Development-only payment simulation. Production must remove this endpoint or protect it with real auth.
        return Result.ok(paymentApplicationService.simulateRechargeSuccess(currentUserResolver.resolve(httpRequest), request.getRechargeNo()));
    }

    private void requireDevelopmentProfile() {
        List<String> profiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .toList();
        boolean production = profiles.contains("prod") || profiles.contains("production");
        boolean development = profiles.contains("dev") || profiles.contains("local");
        if (!development || production) {
            throw new SecurityException("dev simulate recharge endpoint disabled outside development");
        }
    }
}
