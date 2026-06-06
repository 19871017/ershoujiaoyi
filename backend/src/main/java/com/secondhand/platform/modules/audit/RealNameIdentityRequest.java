package com.secondhand.platform.modules.audit;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashSet;
import java.util.Set;

public class RealNameIdentityRequest {
    private String realName;
    private String idTail;
    private Long userId;
    private String senderId;
    private String buyerId;
    private String sellerId;
    private Boolean admin;
    private String role;
    private String identityStatus;
    private String videoIdentityStatus;
    private Boolean videoVerified;
    private final Set<String> extraFields = new HashSet<>();

    @JsonAnySetter
    public void captureExtraField(String name, Object ignoredValue) {
        if (name != null) {
            extraFields.add(name);
        }
    }

    public boolean hasClientDerivedIdentityFields() {
        return userId != null
                || hasText(senderId)
                || hasText(buyerId)
                || hasText(sellerId)
                || admin != null
                || hasText(role)
                || hasText(identityStatus)
                || hasText(videoIdentityStatus)
                || videoVerified != null
                || extraFields.stream().anyMatch(this::isForbiddenIdentityField);
    }

    private boolean isForbiddenIdentityField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim();
        return normalized.equals("userId")
                || normalized.equals("senderId")
                || normalized.equals("buyerId")
                || normalized.equals("sellerId")
                || normalized.equals("admin")
                || normalized.equals("role")
                || normalized.equals("identityStatus")
                || normalized.equals("videoIdentityStatus")
                || normalized.equals("videoVerified");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getIdTail() {
        return idTail;
    }

    public void setIdTail(String idTail) {
        this.idTail = idTail;
    }
}
