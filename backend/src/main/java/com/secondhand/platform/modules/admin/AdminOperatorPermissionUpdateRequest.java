package com.secondhand.platform.modules.admin;

import java.util.List;

public class AdminOperatorPermissionUpdateRequest {
    private List<String> permissions;

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
