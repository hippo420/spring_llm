package app.ai.chat.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 도구 인자 교정 데코레이터: 로컬 모델(qwen2.5 등)이 문자열 파라미터 자리에 JSON 객체를
 * 보내는 quirk — 예: {@code searchNews}의 {@code query}에 {@code {"query":"삼성전자"}} —
 * 를 역직렬화 전에 문자열로 펴서, 그대로 두면 {@code MismatchedInputException}으로 도구
 * 호출이 실패하고 워커가 같은 형식을 연쇄 재시도하는 문제를 막는다.
 *
 * <p>도구 입력 스키마에서 {@code type=string}인 파라미터만 교정 대상으로 삼고, 그 외
 * 파라미터·정상 입력은 원문 그대로 통과시킨다. 교정 규칙은 보수적으로:
 * 같은 이름의 중첩 텍스트 값 → 유일한 텍스트 값 → 텍스트 배열 join → JSON 원문 순.
 * 파싱이 실패하면 원문을 그대로 넘겨 기존 실패 경로(진단 로그 포함)를 유지한다.
 */
public final class ToolArgumentNormalizer implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ToolArgumentNormalizer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ToolCallback delegate;
    private final Set<String> stringParams;

    ToolArgumentNormalizer(ToolCallback delegate) {
        this.delegate = delegate;
        this.stringParams = stringParamsOf(delegate.getToolDefinition());
    }

    private static Set<String> stringParamsOf(ToolDefinition definition) {
        Set<String> names = new HashSet<>();
        try {
            JsonNode properties = JSON.readTree(definition.inputSchema()).path("properties");
            properties.fieldNames().forEachRemaining(name -> {
                if ("string".equals(properties.path(name).path("type").asText())) {
                    names.add(name);
                }
            });
        } catch (Exception e) {
            log.debug("도구 입력 스키마 파싱 실패 — 인자 교정 없이 통과: {}", definition.name());
        }
        return names;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(normalize(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(normalize(toolInput), toolContext);
    }

    private String normalize(String toolInput) {
        if (stringParams.isEmpty() || toolInput == null || toolInput.isBlank()) {
            return toolInput;
        }
        try {
            if (!(JSON.readTree(toolInput) instanceof ObjectNode root)) {
                return toolInput;
            }
            boolean changed = false;
            for (String name : stringParams) {
                JsonNode value = root.get(name);
                if (value != null && value.isContainerNode()) {
                    String flattened = flatten(name, value);
                    log.warn("도구 인자 교정: {}.{} 컨테이너({}) → \"{}\"",
                            delegate.getToolDefinition().name(), name, value, flattened);
                    root.put(name, flattened);
                    changed = true;
                }
            }
            return changed ? JSON.writeValueAsString(root) : toolInput;
        } catch (Exception e) {
            // 교정 실패 시 원문 유지 — 기존 실패 경로에서 진단 로그가 남는다.
            return toolInput;
        }
    }

    private static String flatten(String paramName, JsonNode value) {
        // {"query": {"query": "..."}} — 같은 이름으로 한 번 더 감싼 흔한 형태
        JsonNode nested = value.path(paramName);
        if (nested.isTextual()) {
            return nested.asText();
        }
        // 이름은 달라도 텍스트 값이 하나뿐이면 그 값이 의도한 인자다
        List<String> texts = new ArrayList<>();
        value.forEach(child -> {
            if (child.isTextual()) {
                texts.add(child.asText());
            }
        });
        if (texts.size() == 1) {
            return texts.get(0);
        }
        // ["a","b"] 같은 텍스트 배열은 하나의 검색어로 합친다
        if (value.isArray() && !texts.isEmpty() && texts.size() == value.size()) {
            return String.join(" ", texts);
        }
        return value.toString();
    }
}
