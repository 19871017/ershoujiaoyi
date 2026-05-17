package com.secondhand.platform.modules.user;

public class UserRankingResponse {
    private final Long userId;
    private final int rank;
    private final String nickname;
    private final String avatarUrl;
    private final String gender;
    private final String city;
    private final String bio;
    private final String mainRole;
    private final String videoIdentityStatus;
    private final boolean videoVerified;
    private final int followerCount;
    private final int popularityScore;
    private final int safetyScore;
    private final int guardianScore;
    private final int giftScore;
    private final boolean followedByMe;

    public UserRankingResponse(Long userId, int rank, String nickname, String gender, String city, String bio, String mainRole, int followerCount, boolean followedByMe) {
        this(userId, rank, nickname, gender, city, bio, mainRole, followerCount, followerCount, 0, 0, followedByMe);
    }

    public UserRankingResponse(Long userId, int rank, String nickname, String gender, String city, String bio, String mainRole, int followerCount, int popularityScore, int safetyScore, int guardianScore, boolean followedByMe) {
        this(userId, rank, nickname, gender, city, bio, mainRole, followerCount, popularityScore, safetyScore, guardianScore, popularityScore, followedByMe);
    }

    public UserRankingResponse(Long userId, int rank, String nickname, String gender, String city, String bio, String mainRole, int followerCount, int popularityScore, int safetyScore, int guardianScore, int giftScore, boolean followedByMe) {
        this(userId, rank, nickname, null, gender, city, bio, mainRole, followerCount, popularityScore, safetyScore, guardianScore, giftScore, followedByMe);
    }

    public UserRankingResponse(Long userId, int rank, String nickname, String avatarUrl, String gender, String city, String bio, String mainRole, int followerCount, int popularityScore, int safetyScore, int guardianScore, int giftScore, boolean followedByMe) {
        this(userId, rank, nickname, avatarUrl, gender, city, bio, mainRole, "UNVERIFIED", false, followerCount, popularityScore, safetyScore, guardianScore, giftScore, followedByMe);
    }

    public UserRankingResponse(Long userId, int rank, String nickname, String avatarUrl, String gender, String city, String bio, String mainRole, String videoIdentityStatus, boolean videoVerified, int followerCount, int popularityScore, int safetyScore, int guardianScore, int giftScore, boolean followedByMe) {
        this.userId = userId;
        this.rank = rank;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.city = city;
        this.bio = bio;
        this.mainRole = mainRole;
        this.videoIdentityStatus = videoIdentityStatus == null ? "UNVERIFIED" : videoIdentityStatus;
        this.videoVerified = videoVerified;
        this.followerCount = followerCount;
        this.popularityScore = popularityScore;
        this.safetyScore = safetyScore;
        this.guardianScore = guardianScore;
        this.giftScore = giftScore;
        this.followedByMe = followedByMe;
    }

    public Long getUserId() { return userId; }
    public int getRank() { return rank; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getGender() { return gender; }
    public String getCity() { return city; }
    public String getBio() { return bio; }
    public String getMainRole() { return mainRole; }
    public String getVideoIdentityStatus() { return videoIdentityStatus; }
    public boolean isVideoVerified() { return videoVerified; }
    public int getFollowerCount() { return followerCount; }
    public int getPopularityScore() { return popularityScore; }
    public int getSafetyScore() { return safetyScore; }
    public int getGuardianScore() { return guardianScore; }
    public int getGiftScore() { return giftScore; }
    public boolean isFollowedByMe() { return followedByMe; }
}
