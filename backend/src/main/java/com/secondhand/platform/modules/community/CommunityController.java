package com.secondhand.platform.modules.community;

import com.secondhand.platform.modules.community.application.CommunityApplicationService;
import com.secondhand.platform.modules.community.application.CommunityCommentResponse;
import com.secondhand.platform.modules.community.application.CommunityPostDetailResponse;
import com.secondhand.platform.modules.community.application.CommunityPostPageResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    public Result<List<CommunityPostResponse>> list(@RequestParam(defaultValue = "20") int limit,
                                                    @RequestParam(required = false) String topic,
                                                    HttpServletRequest request) {
        return Result.ok(communityApplicationService.listPublishedPosts(limit, currentUserResolver.resolveOptional(request), topic));
    }

    @GetMapping("/page")
    public Result<CommunityPostPageResponse> page(@RequestParam(defaultValue = "20") int limit,
                                                  @RequestParam(required = false) String topic,
                                                  @RequestParam(required = false) String cursor,
                                                  HttpServletRequest request) {
        return Result.ok(communityApplicationService.listPublishedPostPage(limit, currentUserResolver.resolveOptional(request), topic, cursor));
    }

    @GetMapping("/mine")
    public Result<List<CommunityPostResponse>> mine(@RequestParam(defaultValue = "20") int limit,
                                                    HttpServletRequest request) {
        return Result.ok(communityApplicationService.listMyPosts(currentUserResolver.resolve(request), limit));
    }

    @GetMapping("/{postId}")
    public Result<CommunityPostDetailResponse> detail(@PathVariable String postId, HttpServletRequest request) {
        return Result.ok(communityApplicationService.detail(postId, currentUserResolver.resolveOptional(request)));
    }

    @PostMapping
    public Result<CommunityPostResponse> create(@RequestBody CreateCommunityPostRequest body, HttpServletRequest request) {
        return Result.ok(communityApplicationService.createPost(currentUserResolver.resolve(request), body));
    }

    @PutMapping("/{postId}")
    public Result<CommunityPostDetailResponse> update(@PathVariable Long postId,
                                                      @RequestBody CreateCommunityPostRequest body,
                                                      HttpServletRequest request) {
        return Result.ok(communityApplicationService.updateMyPost(currentUserResolver.resolve(request), postId, body));
    }

    @DeleteMapping("/{postId}")
    public Result<CommunityPostDetailResponse> delete(@PathVariable Long postId, HttpServletRequest request) {
        return Result.ok(communityApplicationService.deleteMyPost(currentUserResolver.resolve(request), postId));
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
