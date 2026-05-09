package com.secondhand.platform.modules.product.application;

import java.math.BigDecimal;
import java.util.List;

public class CreateProductRequest {
    private String title;
    private String description;
    private BigDecimal price;
    private List<String> imageUrls;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
}
