package app.ai.chat.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ToolArgumentNormalizer} — 로컬 모델이 문자열 파라미터에 객체/배열을 보내는
 * quirk 교정 검증. 재현 케이스는 2026-07-19 실측 로그의 searchNews 실패다.
 */
class ToolArgumentNormalizerTest {

    private static final String SCHEMA = """
            {"type":"object","properties":{
              "query":{"type":"string"},
              "limit":{"type":"integer"}}}""";

    private final AtomicReference<String> forwarded = new AtomicReference<>();

    private final ToolCallback normalizer = new ToolArgumentNormalizer(new ToolCallback() {
        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name("searchNews").description("테스트").inputSchema(SCHEMA).build();
        }

        @Override
        public String call(String toolInput) {
            forwarded.set(toolInput);
            return "ok";
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return call(toolInput);
        }
    });

    private String normalized(String toolInput) {
        normalizer.call(toolInput);
        return forwarded.get();
    }

    @Test
    void 정상_입력은_원문_그대로_통과() {
        String input = "{\"query\":\"삼성전자 뉴스\"}";
        assertEquals(input, normalized(input));
    }

    @Test
    void 같은_이름으로_감싼_객체는_내부_값으로_편다() {
        assertEquals("{\"query\":\"삼성전자 뉴스\"}",
                normalized("{\"query\":{\"query\":\"삼성전자 뉴스\"}}"));
    }

    @Test
    void 이름이_달라도_유일한_텍스트_값을_쓴다() {
        assertEquals("{\"query\":\"금리 인하\"}",
                normalized("{\"query\":{\"keyword\":\"금리 인하\"}}"));
    }

    @Test
    void 텍스트_배열은_하나의_검색어로_합친다() {
        assertEquals("{\"query\":\"삼성전자 반도체\"}",
                normalized("{\"query\":[\"삼성전자\",\"반도체\"]}"));
    }

    @Test
    void 문자열_파라미터가_아니면_건드리지_않는다() {
        String input = "{\"query\":\"환율\",\"limit\":5}";
        assertEquals(input, normalized(input));
    }
}
