package app.ai.chat;

import app.ai.chat.feature.ChatFeature;

public record ChatMessageRequest(String content, ChatFeature feature) {

    public ChatFeature featureOrDefault() {
        return feature != null ? feature : ChatFeature.GENERAL_CHAT;
    }
}
