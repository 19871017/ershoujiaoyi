package com.secondhand.platform.modules.community.application;

import java.util.List;

public class CreateCommunityPostRequest {
    private String title;
    private String topic;
    private String content;
    private List<String> imageUrls;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
}
