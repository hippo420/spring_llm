package app.ai.chat.feature;

public enum ChatFeature {
    GENERAL_CHAT("일반 대화"),
    HISTORY("대화영속성"),
    DOC_RAG("문서 기반 RAG");

    private final String label;

    ChatFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
