package app.ai.chat.dto;

import app.ai.chat.feature.ChatFeature;

public record ChatMessageRequest(String content, ChatFeature feature) {

    public ChatFeature featureOrDefault() {
        return feature != null ? feature : ChatFeature.GENERAL_CHAT;
    }
}
