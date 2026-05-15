package com.secondhand.platform.modules.media;

import com.secondhand.platform.modules.media.application.CreateMediaUploadTicketRequest;
import com.secondhand.platform.modules.media.application.MediaUploadTicketResponse;
import com.secondhand.platform.modules.media.application.MediaUploadTicketService;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/upload-tickets/{ticketNo}/file")
    public Result<MediaUploadTicketResponse> uploadFile(@PathVariable String ticketNo,
                                                        @RequestHeader(value = "X-Upload-Token", required = false) String uploadToken,
                                                        @RequestParam("file") MultipartFile file,
                                                        HttpServletRequest request) {
        long userId = currentUserResolver.resolve(request);
        return Result.ok(mediaUploadTicketService.storeUploadedFile(userId, ticketNo, uploadToken, file));
    }
}
