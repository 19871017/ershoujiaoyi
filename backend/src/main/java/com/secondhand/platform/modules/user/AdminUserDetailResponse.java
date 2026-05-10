package com.secondhand.platform.modules.user;

public class AdminUserDetailResponse {
    private Long userId;
    private String userNo;
    private String maskedPhone;
    private String nickname;
    private String status;
    private String mainRole;
    private String city;
    private String bio;
    private String videoIdentityStatus;
    private boolean videoVerified;
    private String createdAt;
    private String updatedAt;

    public AdminUserDetailResponse(Long userId,
                                   String userNo,
                                   String maskedPhone,
                                   String nickname,
                                   String status,
                                   String mainRole,
                                   String city,
                                   String bio,
                                   String videoIdentityStatus,
                                   boolean videoVerified,
                                   String createdAt,
                                   String updatedAt) {
        this.userId = userId;
        this.userNo = userNo;
        this.maskedPhone = maskedPhone;
        this.nickname = nickname;
        this.status = status;
        this.mainRole = mainRole;
        this.city = city;
        this.bio = bio;
        this.videoIdentityStatus = videoIdentityStatus == null ? "UNVERIFIED" : videoIdentityStatus;
        this.videoVerified = videoVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getUserId() { return userId; }
    public String getUserNo() { return userNo; }
    public String getMaskedPhone() { return maskedPhone; }
    public String getNickname() { return nickname; }
    public String getStatus() { return status; }
    public String getMainRole() { return mainRole; }
    public String getCity() { return city; }
    public String getBio() { return bio; }
    public String getVideoIdentityStatus() { return videoIdentityStatus; }
    public boolean isVideoVerified() { return videoVerified; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
}
