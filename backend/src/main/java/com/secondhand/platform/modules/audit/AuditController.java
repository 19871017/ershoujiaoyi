package com.secondhand.platform.modules.audit;

import com.secondhand.platform.modules.audit.application.AuditApplicationService;
import com.secondhand.platform.modules.audit.application.AuditRecordResponse;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditApplicationService auditApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public AuditController(AuditApplicationService auditApplicationService, CurrentUserResolver currentUserResolver) {
        this.auditApplicationService = auditApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/reports")
    public Result<AuditRecordResponse> submitReport(@RequestBody ReportRequest body, HttpServletRequest request) {
        Long userId = currentUserResolver.resolve(request);
        return Result.ok(auditApplicationService.submitReport(
                userId,
                body == null ? null : body.getTargetType(),
                body == null ? null : body.getTargetId(),
                body == null ? null : body.getReason(),
                body == null ? null : body.getDescription(),
                body == null ? null : body.getEvidenceUrls()
        ));
    }

    @PostMapping("/video-identity")
    public Result<AuditRecordResponse> submitVideoIdentity(@RequestBody VideoIdentityRequest body, HttpServletRequest request) {
        if (body != null && body.hasClientDerivedIdentityFields()) {
            throw new IllegalArgumentException("identity fields must be server-derived");
        }
        Long userId = currentUserResolver.resolve(request);
        return Result.ok(auditApplicationService.submitVideoIdentity(
                userId,
                body == null ? null : body.getVideoUrl(),
                body == null ? null : body.getDescription()
        ));
    }
}
