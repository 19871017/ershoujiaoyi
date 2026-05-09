package com.secondhand.platform.modules.community;

import com.secondhand.platform.modules.community.application.CommunityApplicationService;
import com.secondhand.platform.modules.community.application.CommunityCommentResponse;
import com.secondhand.platform.modules.community.application.CommunityPostDetailResponse;
import com.secondhand.platform.modules.community.application.CommunityPostResponse;
import com.secondhand.platform.modules.community.application.CreateCommunityCommentRequest;
import com.secondhand.platform.modules.community.application.CreateCommunityPostRequest;
import com.secondhand.platform.shared.kernel.Result;
import com.secondhand.platform.shared.web.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts")
public class CommunityController {
    private final CommunityApplicationService communityApplicationService;
    private final CurrentUserResolver currentUserResolver;

    public CommunityController(CommunityApplicationService communityApplicationService, CurrentUserResolver currentUserResolver) {
        this.communityApplicationService = communityApplicationService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public Result<List<CommunityPostResponse>> list(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(communityApplicationService.listPublishedPosts(limit));
    }

    @GetMapping("/{postId}")
    public Result<CommunityPostDetailResponse> detail(@PathVariable String postId, HttpServletRequest request) {
        return Result.ok(communityApplicationService.detail(postId, currentUserResolver.resolve(request)));
    }

    @PostMapping
    public Result<CommunityPostResponse> create(@RequestBody CreateCommunityPostRequest body, HttpServletRequest request) {
        return Result.ok(communityApplicationService.createPost(currentUserResolver.resolve(request), body));
    }

    @PostMapping("/{postId}/comments")
    public Result<CommunityCommentResponse> comment(@PathVariable Long postId,
                                                    @RequestBody CreateCommunityCommentRequest body,
                                                    HttpServletRequest request) {
        return Result.ok(communityApplicationService.addComment(currentUserResolver.resolve(request), postId, body));
    }

    @PostMapping("/{postId}/likes")
    public Result<CommunityPostDetailResponse> like(@PathVariable Long postId, HttpServletRequest request) {
        return Result.ok(communityApplicationService.likePost(currentUserResolver.resolve(request), postId));
    }

    @DeleteMapping("/{postId}/likes")
    public Result<CommunityPostDetailResponse> unlike(@PathVariable Long postId, HttpServletRequest request) {
        return Result.ok(communityApplicationService.unlikePost(currentUserResolver.resolve(request), postId));
    }
}
