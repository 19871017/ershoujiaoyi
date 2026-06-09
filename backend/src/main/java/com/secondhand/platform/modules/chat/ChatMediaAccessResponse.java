package com.secondhand.platform.modules.chat;

public record ChatMediaAccessResponse(String storageUrl,
                                      Long conversationId,
                                      Long senderId,
                                      Long receiverId,
                                      String contentType,
                                      Long fileSize) {
}
