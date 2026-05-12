package com.secondhand.platform.modules.admin;

import java.util.List;

public record AdminOperatorPermissionResponse(
        Long userId,
        String userNo,
        String nickname,
        String status,
        List<String> permissions
) {
}
