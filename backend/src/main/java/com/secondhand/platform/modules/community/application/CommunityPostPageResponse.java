package com.secondhand.platform.modules.community.application;

import java.util.List;

public class CommunityPostPageResponse {
    private final List<CommunityPostResponse> posts;
    private final String nextCursor;
    private final boolean hasMore;

    public CommunityPostPageResponse(List<CommunityPostResponse> posts, String nextCursor, boolean hasMore) {
        this.posts = posts == null ? List.of() : List.copyOf(posts);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<CommunityPostResponse> getPosts() { return posts; }
    public String getNextCursor() { return nextCursor; }
    public boolean getHasMore() { return hasMore; }
}
