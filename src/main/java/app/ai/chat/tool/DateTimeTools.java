package app.ai.chat.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 현재 시각 도구 (docs/09-tool-calling-design.md 5.1절) — 모델은 오늘 날짜를 모른다.
 * "이번 주", "어제" 같은 상대 시점 해석의 기준을 제공하는 가장 값싸고 효과 큰 도구.
 */
@Component
public class DateTimeTools {

    @Tool(description = "현재 날짜와 시각을 반환한다. 오늘/이번주/어제 등 상대적 시점 해석에 사용.")
    public String getCurrentDateTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (E)", Locale.KOREAN));
    }
}
