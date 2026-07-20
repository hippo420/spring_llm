package app.ai.chat.feature;

public enum ChatFeature {
    GENERAL_CHAT("일반 대화"),
    HISTORY("대화영속성"),
    DOC_RAG("문서 기반 RAG"),
    TOOL_RAG("TOOL 증강 RAG"),
    AGENT_TEAM("멀티 에이전트"),
    CRITIC_AGENT_TEAM("멀티 에이전트 + 비평가");

    private final String label;

    ChatFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
