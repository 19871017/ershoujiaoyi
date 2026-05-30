package com.secondhand.platform.modules.user;

import java.util.List;

public class UserProfileResponse {
    private Long userId;
    private String userNo;
    private String nickname;
    private String avatarUrl;
    private String mainRole;
    private String gender;
    private String city;
    private String bio;
    private String videoIdentityStatus;
    private boolean videoVerified;
    private String videoIdentityUrl;
    private List<String> showcaseImageUrls;
    private boolean followedByMe;
    private int followerCount;
    private int followingCount;
    private int sellerCharmScore;
    private int buyerPowerScore;

    public UserProfileResponse(Long userId, String nickname, String mainRole) {
        this(userId, null, nickname, null, mainRole, null, null, null, "UNVERIFIED", false, null, List.of(), false, 0, 0, 0, 0);
    }

    public UserProfileResponse(Long userId, String nickname, String mainRole, String videoIdentityStatus, boolean videoVerified) {
        this(userId, null, nickname, null, mainRole, null, null, null, videoIdentityStatus, videoVerified, null, List.of(), false, 0, 0, 0, 0);
    }

    public UserProfileResponse(Long userId, String nickname, String mainRole, String videoIdentityStatus, boolean videoVerified, boolean followedByMe) {
        this(userId, null, nickname, null, mainRole, null, null, null, videoIdentityStatus, videoVerified, null, List.of(), followedByMe, 0, 0, 0, 0);
    }

    public UserProfileResponse(Long userId, String userNo, String nickname, String avatarUrl, String mainRole, String gender, String city, String bio, String videoIdentityStatus, boolean videoVerified, boolean followedByMe) {
        this(userId, userNo, nickname, avatarUrl, mainRole, gender, city, bio, videoIdentityStatus, videoVerified, null, List.of(), followedByMe, 0, 0, 0, 0);
    }

    public UserProfileResponse(Long userId, String userNo, String nickname, String avatarUrl, String mainRole, String gender, String city, String bio, String videoIdentityStatus, boolean videoVerified, boolean followedByMe, int followerCount, int followingCount, int sellerCharmScore, int buyerPowerScore) {
        this(userId, userNo, nickname, avatarUrl, mainRole, gender, city, bio, videoIdentityStatus, videoVerified, null, List.of(), followedByMe, followerCount, followingCount, sellerCharmScore, buyerPowerScore);
    }

    public UserProfileResponse(Long userId, String userNo, String nickname, String avatarUrl, String mainRole, String gender, String city, String bio, String videoIdentityStatus, boolean videoVerified, String videoIdentityUrl, List<String> showcaseImageUrls, boolean followedByMe, int followerCount, int followingCount, int sellerCharmScore, int buyerPowerScore) {
        this.userId = userId;
        this.userNo = userNo;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.mainRole = mainRole;
        this.gender = gender;
        this.city = city;
        this.bio = bio;
        this.videoIdentityStatus = videoIdentityStatus == null ? "UNVERIFIED" : videoIdentityStatus;
        this.videoVerified = videoVerified;
        this.videoIdentityUrl = (videoVerified || "PENDING".equals(this.videoIdentityStatus)) ? videoIdentityUrl : null;
        this.showcaseImageUrls = videoVerified ? List.copyOf(showcaseImageUrls == null ? List.of() : showcaseImageUrls) : List.of();
        this.followedByMe = followedByMe;
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.sellerCharmScore = sellerCharmScore;
        this.buyerPowerScore = buyerPowerScore;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNo() {
        return userNo;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getMainRole() {
        return mainRole;
    }

    public String getGender() {
        return gender;
    }

    public String getCity() {
        return city;
    }

    public String getBio() {
        return bio;
    }

    public String getVideoIdentityStatus() {
        return videoIdentityStatus;
    }

    public boolean isVideoVerified() {
        return videoVerified;
    }

    public String getVideoIdentityUrl() {
        return videoIdentityUrl;
    }

    public List<String> getShowcaseImageUrls() {
        return showcaseImageUrls;
    }

    public boolean isFollowedByMe() {
        return followedByMe;
    }

    public int getFollowerCount() {
        return followerCount;
    }

    public int getFollowingCount() {
        return followingCount;
    }

    public int getSellerCharmScore() {
        return sellerCharmScore;
    }

    public int getBuyerPowerScore() {
        return buyerPowerScore;
    }
}
