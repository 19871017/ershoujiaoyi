package com.secondhand.platform.modules.user;

import java.util.List;

public class AccountSecurityResponse {
    private Long userId;
    private String maskedPhone;
    private String securityScore;
    private List<LoginDeviceResponse> recentDevices;

    public AccountSecurityResponse(Long userId, String maskedPhone, String securityScore, List<LoginDeviceResponse> recentDevices) {
        this.userId = userId;
        this.maskedPhone = maskedPhone;
        this.securityScore = securityScore;
        this.recentDevices = recentDevices == null ? List.of() : List.copyOf(recentDevices);
    }

    public Long getUserId() {
        return userId;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public String getSecurityScore() {
        return securityScore;
    }

    public List<LoginDeviceResponse> getRecentDevices() {
        return recentDevices;
    }

    public static class LoginDeviceResponse {
        private String deviceName;
        private String loginAt;
        private String city;
        private String status;

        public LoginDeviceResponse(String deviceName, String loginAt, String city, String status) {
            this.deviceName = deviceName;
            this.loginAt = loginAt;
            this.city = city;
            this.status = status;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getLoginAt() {
            return loginAt;
        }

        public String getCity() {
            return city;
        }

        public String getStatus() {
            return status;
        }
    }
}
