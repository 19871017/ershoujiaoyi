package com.secondhand.platform.modules.user;

public class UserRankingResponse {
    private final Long userId;
    private final int rank;
    private final String nickname;
    private final String gender;
    private final String city;
    private final String bio;
    private final String mainRole;
    private final int followerCount;
    private final int popularityScore;
    private final int safetyScore;
    private final int guardianScore;
    private final boolean followedByMe;

    public UserRankingResponse(Long userId, int rank, String nickname, String gender, String city, String bio, String mainRole, int followerCount, boolean followedByMe) {
        this(userId, rank, nickname, gender, city, bio, mainRole, followerCount, followerCount, 0, 0, followedByMe);
    }

    public UserRankingResponse(Long userId, int rank, String nickname, String gender, String city, String bio, String mainRole, int followerCount, int popularityScore, int safetyScore, int guardianScore, boolean followedByMe) {
        this.userId = userId;
        this.rank = rank;
        this.nickname = nickname;
        this.gender = gender;
        this.city = city;
        this.bio = bio;
        this.mainRole = mainRole;
        this.followerCount = followerCount;
        this.popularityScore = popularityScore;
        this.safetyScore = safetyScore;
        this.guardianScore = guardianScore;
        this.followedByMe = followedByMe;
    }

    public Long getUserId() { return userId; }
    public int getRank() { return rank; }
    public String getNickname() { return nickname; }
    public String getGender() { return gender; }
    public String getCity() { return city; }
    public String getBio() { return bio; }
    public String getMainRole() { return mainRole; }
    public int getFollowerCount() { return followerCount; }
    public int getPopularityScore() { return popularityScore; }
    public int getSafetyScore() { return safetyScore; }
    public int getGuardianScore() { return guardianScore; }
    public boolean isFollowedByMe() { return followedByMe; }
}
