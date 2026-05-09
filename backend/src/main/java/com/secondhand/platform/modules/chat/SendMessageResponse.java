package com.secondhand.platform.modules.chat;

public class SendMessageResponse {
    private ChatMessageAck ack;

    public SendMessageResponse() {
    }

    public SendMessageResponse(ChatMessageAck ack) {
        this.ack = ack;
    }

    public ChatMessageAck getAck() {
        return ack;
    }

    public void setAck(ChatMessageAck ack) {
        this.ack = ack;
    }
}
