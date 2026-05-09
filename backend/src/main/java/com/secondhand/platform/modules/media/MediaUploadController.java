package com.secondhand.platform.modules.media;

import com.secondhand.platform.modules.media.application.CreateMediaUploadTicketRequest;
import com.secondhand.platform.modules.media.application.MediaUploadTicketResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaUploadController {
    private final MediaUploadTicketService mediaUploadTicketService;
    private final CurrentUserResolver currentUserResolver;

    public MediaUploadController(MediaUploadTicketService mediaUploadTicketService, CurrentUserResolver currentUserResolver) {
        this.mediaUploadTicketService = mediaUploadTicketService;
        this.currentUserResolver = currentUserResolver;
    }

    @PostMapping("/upload-tickets")
    public Result<MediaUploadTicketResponse> issue(@RequestBody CreateMediaUploadTicketRequest body, HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(mediaUploadTicketService.issue(
                userId,
                body == null ? null : body.getScene(),
                body == null ? null : body.getContentType(),
                body == null ? null : body.getFileSize(),
                body == null ? null : body.getFilename()
        ));
    }
}
