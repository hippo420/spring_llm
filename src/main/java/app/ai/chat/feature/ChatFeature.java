package app.ai.chat.feature;

public enum ChatFeature {
    GENERAL_CHAT("일반 대화"),
    SUMMARIZE("요약"),
    TRANSLATE("번역"),
    CODE_EXPLAIN("코드 설명"),
    CODE_REVIEW("코드 리뷰"),
    GRAMMAR_CHECK("맞춤법 교정"),
    EMAIL_WRITE("이메일 작성"),
    BRAINSTORM("아이디어 브레인스토밍"),
    DOC_QA("문서 기반 Q&A"),
    SENTIMENT_ANALYSIS("감정 분석");

    private final String label;

    ChatFeature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
