package com.secondhand.platform.modules.user;

public class UserProfileResponse {
    private Long userId;
    private String nickname;
    private String mainRole;
    private String videoIdentityStatus;
    private boolean videoVerified;

    public UserProfileResponse(Long userId, String nickname, String mainRole) {
        this(userId, nickname, mainRole, "UNVERIFIED", false);
    }

    public UserProfileResponse(Long userId, String nickname, String mainRole, String videoIdentityStatus, boolean videoVerified) {
        this.userId = userId;
        this.nickname = nickname;
        this.mainRole = mainRole;
        this.videoIdentityStatus = videoIdentityStatus == null ? "UNVERIFIED" : videoIdentityStatus;
        this.videoVerified = videoVerified;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getMainRole() {
        return mainRole;
    }

    public String getVideoIdentityStatus() {
        return videoIdentityStatus;
    }

    public boolean isVideoVerified() {
        return videoVerified;
    }
}
