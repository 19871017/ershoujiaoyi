package com.secondhand.platform.modules.user;

import java.util.List;

public class UpdateUserProfileRequest {
    private String nickname;
    private String avatarUrl;
    private String gender;
    private String city;
    private String bio;
    private List<String> showcaseImageUrls;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<String> getShowcaseImageUrls() {
        return showcaseImageUrls;
    }

    public void setShowcaseImageUrls(List<String> showcaseImageUrls) {
        this.showcaseImageUrls = showcaseImageUrls;
    }

}
