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
    private String identityStatus;
    private String videoIdentityStatus;
    private boolean videoVerified;
    private String createdAt;
    private String updatedAt;
    private AdminUserOpsSummary opsSummary;

    public AdminUserDetailResponse(Long userId,
                                   String userNo,
                                   String maskedPhone,
                                   String nickname,
                                   String status,
                                   String mainRole,
                                   String city,
                                   String bio,
                                   String identityStatus,
                                   String videoIdentityStatus,
                                   boolean videoVerified,
                                   String createdAt,
                                   String updatedAt) {
        this(userId, userNo, maskedPhone, nickname, status, mainRole, city, bio, identityStatus,
                videoIdentityStatus, videoVerified, createdAt, updatedAt, null);
    }

    public AdminUserDetailResponse(Long userId,
                                   String userNo,
                                   String maskedPhone,
                                   String nickname,
                                   String status,
                                   String mainRole,
                                   String city,
                                   String bio,
                                   String identityStatus,
                                   String videoIdentityStatus,
                                   boolean videoVerified,
                                   String createdAt,
                                   String updatedAt,
                                   AdminUserOpsSummary opsSummary) {
        this.userId = userId;
        this.userNo = userNo;
        this.maskedPhone = maskedPhone;
        this.nickname = nickname;
        this.status = status;
        this.mainRole = mainRole;
        this.city = city;
        this.bio = bio;
        this.identityStatus = identityStatus == null ? "UNVERIFIED" : identityStatus;
        this.videoIdentityStatus = videoIdentityStatus == null ? "UNVERIFIED" : videoIdentityStatus;
        this.videoVerified = videoVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.opsSummary = opsSummary;
    }

    public Long getUserId() { return userId; }
    public String getUserNo() { return userNo; }
    public String getMaskedPhone() { return maskedPhone; }
    public String getNickname() { return nickname; }
    public String getStatus() { return status; }
    public String getMainRole() { return mainRole; }
    public String getCity() { return city; }
    public String getBio() { return bio; }
    public String getIdentityStatus() { return identityStatus; }
    public String getVideoIdentityStatus() { return videoIdentityStatus; }
    public boolean isVideoVerified() { return videoVerified; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public AdminUserOpsSummary getOpsSummary() { return opsSummary; }

    public static class AdminUserOpsSummary {
        private long orderCount;
        private long paidOrderCount;
        private long afterSalesCount;
        private long pendingAfterSalesCount;
        private long reportCount;
        private long pendingReportCount;
        private long withdrawalCount;
        private long pendingWithdrawalCount;
        private long chatConversationCount;
        private String lastOrderNo;
        private String lastAfterSalesNo;
        private String lastWithdrawalNo;
        private Long lastChatConversationId;

        public AdminUserOpsSummary(long orderCount,
                                   long paidOrderCount,
                                   long afterSalesCount,
                                   long pendingAfterSalesCount,
                                   long reportCount,
                                   long pendingReportCount,
                                   long withdrawalCount,
                                   long pendingWithdrawalCount,
                                   long chatConversationCount,
                                   String lastOrderNo,
                                   String lastAfterSalesNo,
                                   String lastWithdrawalNo,
                                   Long lastChatConversationId) {
            this.orderCount = orderCount;
            this.paidOrderCount = paidOrderCount;
            this.afterSalesCount = afterSalesCount;
            this.pendingAfterSalesCount = pendingAfterSalesCount;
            this.reportCount = reportCount;
            this.pendingReportCount = pendingReportCount;
            this.withdrawalCount = withdrawalCount;
            this.pendingWithdrawalCount = pendingWithdrawalCount;
            this.chatConversationCount = chatConversationCount;
            this.lastOrderNo = lastOrderNo;
            this.lastAfterSalesNo = lastAfterSalesNo;
            this.lastWithdrawalNo = lastWithdrawalNo;
            this.lastChatConversationId = lastChatConversationId;
        }

        public long getOrderCount() { return orderCount; }
        public long getPaidOrderCount() { return paidOrderCount; }
        public long getAfterSalesCount() { return afterSalesCount; }
        public long getPendingAfterSalesCount() { return pendingAfterSalesCount; }
        public long getReportCount() { return reportCount; }
        public long getPendingReportCount() { return pendingReportCount; }
        public long getWithdrawalCount() { return withdrawalCount; }
        public long getPendingWithdrawalCount() { return pendingWithdrawalCount; }
        public long getChatConversationCount() { return chatConversationCount; }
        public String getLastOrderNo() { return lastOrderNo; }
        public String getLastAfterSalesNo() { return lastAfterSalesNo; }
        public String getLastWithdrawalNo() { return lastWithdrawalNo; }
        public Long getLastChatConversationId() { return lastChatConversationId; }
    }
}
