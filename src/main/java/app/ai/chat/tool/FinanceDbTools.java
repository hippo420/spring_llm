package app.ai.chat.tool;

import app.ai.finance.FinanceDbRepository;
import app.ai.finance.FinanceDbRepository.AccountRow;
import app.ai.finance.FinanceDbRepository.EconomicEvent;
import app.ai.finance.FinanceDbRepository.InvestorRow;
import app.ai.finance.FinanceDbRepository.PriceRow;
import app.ai.finance.FinanceDbRepository.RankMetric;
import app.ai.finance.FinanceDbRepository.RankRow;
import app.ai.finance.FinanceDbRepository.ReportKey;
import app.ai.finance.FinanceDbRepository.StockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사내 금융 DB 조회 도구 (docs/12-finance-db-query-selection.md).
 *
 * <p>화이트리스트 방식: SQL은 전부 {@link FinanceDbRepository}에 고정돼 있고, 모델은 도구
 * 선택과 파라미터 값만 결정한다. 순위 기준·시장 같은 선택형 파라미터는 여기서 매핑
 * 테이블로 검증한 뒤에만 리포지토리로 넘긴다. 실패는 예외 대신 "DB 조회 실패: 사유"
 * 문자열로 반환한다 (09 설계 7절).
 *
 * <p>결과에는 항상 기준일자를 넣는다 — 모델이 데이터 신선도를 스스로 판단하는 근거지만,
 * 답변에 "기준일은 ...", "최신 적재일 기준" 같은 문구로 반복하지는 않는다(사용자 요청,
 * 2026-07-19). 다만 일별 시세·경제 지표 일정처럼 날짜 자체가 답변 내용인 경우는 예외다.
 */
@Component
public class FinanceDbTools {

    private static final Logger log = LoggerFactory.getLogger(FinanceDbTools.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, RankMetric> METRICS = Map.of(
            "시가총액", RankMetric.MARKET_CAP,
            "상승률", RankMetric.TOP_GAINERS,
            "하락률", RankMetric.TOP_LOSERS,
            "거래대금", RankMetric.TRADE_VALUE);
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ", "KONEX");
    private static final Map<String, String> REPORT_NAMES = Map.of(
            "11011", "사업보고서(연간)", "11014", "3분기보고서",
            "11012", "반기보고서", "11013", "1분기보고서");
    private static final Map<String, String> ACTORS = Map.of(
            "FOREIGN", "외국인", "INSTITUTION", "기관", "PERSONAL", "개인");

    private final FinanceDbRepository repository;

    public FinanceDbTools(FinanceDbRepository repository) {
        this.repository = repository;
    }

    @Tool(description = """
            종목명이나 6자리 종목코드로 상장 종목을 검색해 종목코드·시장·상장일·대표이사 등
            기본 정보를 반환한다. 회사의 정확한 이름이나 코드가 불확실할 때 먼저 사용한다.""")
    public String searchStock(
            @ToolParam(description = "종목명 또는 6자리 종목코드 (예: 삼성전자, 005930)") String query) {
        try {
            List<StockInfo> stocks = repository.findStocks(query.strip(), 5);
            if (stocks.isEmpty()) {
                return "'" + query + "' 종목을 찾지 못했다.";
            }
            StringBuilder out = new StringBuilder("종목 검색 결과:\n");
            for (StockInfo s : stocks) {
                out.append("- ").append(s.name()).append(" (").append(s.shortCode()).append(", ")
                        .append(s.market()).append(") 정식명: ").append(s.fullName());
                if (s.listedDate() != null) out.append(", 상장일 ").append(dashDate(s.listedDate()));
                if (s.ceo() != null) out.append(", 대표이사 ").append(s.ceo());
                if (s.industryCode() != null) out.append(", 섹터코드 ").append(s.industryCode());
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("종목 검색", e);
        }
    }

    @Tool(description = """
            종목의 일별 시세(시가/고가/저가/종가/등락률/거래량/시가총액)를 사내 DB에서 조회한다.
            현재가·주가 흐름 질문에 사용한다. 기간을 주지 않으면 최근 5영업일을 반환한다.""")
    public String getStockPrice(
            @ToolParam(description = "종목명 또는 6자리 종목코드") String company,
            @ToolParam(description = "조회 시작일 YYYYMMDD (선택)", required = false) String fromDate,
            @ToolParam(description = "조회 종료일 YYYYMMDD (선택)", required = false) String toDate) {
        try {
            Optional<StockInfo> stock = resolve(company);
            if (stock.isEmpty()) {
                return "'" + company + "' 종목을 찾지 못했다. searchStock으로 먼저 확인하라.";
            }
            List<PriceRow> prices = repository.findPrices(
                    stock.get().shortCode(), normalizeDate(fromDate), normalizeDate(toDate), 30);
            if (prices.isEmpty()) {
                return stock.get().name() + " 시세 데이터가 해당 기간에 없다.";
            }
            StringBuilder out = new StringBuilder(stock.get().name())
                    .append('(').append(stock.get().shortCode())
                    .append(") 일별 시세 — 최신 적재일 ").append(dashDate(prices.getFirst().date()))
                    .append(" (적재일 자체는 답변에 언급하지 말 것; 아래 개별 날짜는 정상 보고):\n");
            for (PriceRow p : prices) {
                out.append('[').append(dashDate(p.date())).append("] 종가 ")
                        .append(comma(p.close())).append("원 (").append(percent(p.flucRate()))
                        .append(") | 시가 ").append(comma(p.open()))
                        .append(" 고가 ").append(comma(p.high()))
                        .append(" 저가 ").append(comma(p.low()))
                        .append(" | 거래량 ").append(comma(p.volume())).append('주');
                if (p.marketCap() != null) out.append(" | 시가총액 ").append(krw(p.marketCap()));
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("시세 조회", e);
        }
    }

    @Tool(description = """
            기준 지표로 주식 순위를 조회한다 (최신 거래일 기준). "시총 상위", "많이 오른 종목",
            "거래대금 많은 종목" 류의 질문에 사용한다.""")
    public String getTopStocks(
            @ToolParam(description = "순위 기준: 시가총액 | 상승률 | 하락률 | 거래대금") String metric,
            @ToolParam(description = "시장 필터: KOSPI | KOSDAQ | KONEX (선택, 생략 시 전체)", required = false) String market,
            @ToolParam(description = "결과 수 (선택, 기본 10, 최대 20)", required = false) Integer limit) {
        try {
            RankMetric rankMetric = METRICS.get(metric == null ? "" : metric.strip());
            if (rankMetric == null) {
                return "지원하지 않는 순위 기준: " + metric + " — 시가총액|상승률|하락률|거래대금 중 하나를 사용하라.";
            }
            String marketFilter = null;
            if (market != null && !market.isBlank()) {
                marketFilter = market.strip().toUpperCase();
                if (!MARKETS.contains(marketFilter)) {
                    return "지원하지 않는 시장: " + market + " — KOSPI|KOSDAQ|KONEX 중 하나를 사용하라.";
                }
            }
            int rows = clamp(limit, 10, 1, 20);
            List<RankRow> ranks = repository.findTopStocks(rankMetric, marketFilter, rows);
            if (ranks.isEmpty()) {
                return "순위 데이터가 없다.";
            }
            StringBuilder out = new StringBuilder(metric.strip()).append(" 상위 ").append(ranks.size())
                    .append("종목 (기준일 ").append(dashDate(ranks.getFirst().date()))
                    .append(marketFilter == null ? "" : ", " + marketFilter)
                    .append(", 기준일은 답변에 언급하지 말 것):\n");
            int i = 1;
            for (RankRow r : ranks) {
                out.append('[').append(i++).append("] ").append(r.name())
                        .append('(').append(r.code()).append(", ").append(r.market())
                        .append(") 종가 ").append(comma(r.close())).append("원 (")
                        .append(percent(r.flucRate())).append(')');
                if (r.marketCap() != null) out.append(", 시가총액 ").append(krw(r.marketCap()));
                if (r.tradeValue() != null) out.append(", 거래대금 ").append(krw(r.tradeValue().longValue()));
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("순위 조회", e);
        }
    }

    @Tool(description = """
            종목의 최근 투자자별(외국인/기관/개인) 순매수 동향과 주도 매매 주체를 조회한다.
            "외국인이 사고 있어?", "수급이 어때?" 류의 질문에 사용한다.""")
    public String getInvestorTrend(
            @ToolParam(description = "종목명 또는 6자리 종목코드") String company,
            @ToolParam(description = "조회 일수 (선택, 기본 5, 최대 20)", required = false) Integer days) {
        try {
            Optional<StockInfo> stock = resolve(company);
            if (stock.isEmpty()) {
                return "'" + company + "' 종목을 찾지 못했다. searchStock으로 먼저 확인하라.";
            }
            List<InvestorRow> rows = repository.findInvestorTrend(stock.get().shortCode(), clamp(days, 5, 1, 20));
            if (rows.isEmpty()) {
                return stock.get().name() + " 수급 데이터가 없다.";
            }
            StringBuilder out = new StringBuilder(stock.get().name())
                    .append('(').append(stock.get().shortCode()).append(") 투자자별 순매수 — 최신 적재일 ")
                    .append(dashDate(rows.getFirst().date()))
                    .append(" (적재일 자체는 답변에 언급하지 말 것; 양수=순매수, 음수=순매도):\n");
            for (InvestorRow r : rows) {
                out.append('[').append(dashDate(r.date())).append("] 종가 ").append(comma(r.close()))
                        .append("원 (").append(percent(r.flucRate()))
                        .append(") | 외국인 ").append(signedComma(r.frgnNetQty())).append('주');
                if (r.frgnNetValueMillions() != null) {
                    out.append('(').append(krwSigned(r.frgnNetValueMillions() * 1_000_000L)).append(')');
                }
                out.append(" | 기관 ").append(signedComma(r.instNetQty())).append('주')
                        .append(" | 개인 ").append(signedComma(r.prsnNetQty())).append('주')
                        .append(" | 주도: ").append(ACTORS.getOrDefault(r.dominantActor(), String.valueOf(r.dominantActor())))
                        .append('\n');
            }
            InvestorRow latest = rows.getFirst();
            if (latest.totalSignal() != null) {
                out.append("종합 매매 시그널(최근일): ").append(latest.totalSignal())
                        .append(" (양수=매수 우위, 음수=매도 우위), 시장 국면: ")
                        .append(latest.marketRegime()).append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("수급 조회", e);
        }
    }

    @Tool(description = """
            종목의 재무제표 주요 계정(자산/부채/자본총계, 매출액, 영업이익, 당기순이익 등)을
            DART 공시 기준으로 조회한다. 연도를 주지 않으면 최신 보고서를 사용한다.""")
    public String getFinancialStatement(
            @ToolParam(description = "종목명 또는 6자리 종목코드") String company,
            @ToolParam(description = "사업연도 YYYY (선택, 생략 시 최신)", required = false) String year) {
        try {
            Optional<StockInfo> stock = resolve(company);
            if (stock.isEmpty()) {
                return "'" + company + "' 종목을 찾지 못했다. searchStock으로 먼저 확인하라.";
            }
            String code = stock.get().shortCode();
            String yearFilter = (year == null || year.isBlank()) ? null : year.strip();
            Optional<ReportKey> report = repository.findLatestReport(code, yearFilter);
            if (report.isEmpty()) {
                return stock.get().name() + "의 재무제표 데이터가 DB에 없다. 문서 검색이나 뉴스 검색으로 대신 확인하라.";
            }
            List<AccountRow> accounts = repository.findAccounts(code, report.get());
            StringBuilder out = new StringBuilder(stock.get().name())
                    .append('(').append(code).append(") ").append(report.get().year()).append("년 ")
                    .append(REPORT_NAMES.getOrDefault(report.get().reportCode(), report.get().reportCode()))
                    .append(", ").append("CFS".equals(report.get().fsDiv()) ? "연결재무제표" : "개별재무제표")
                    .append(":\n");
            String section = "";
            for (AccountRow a : accounts) {
                if (!a.statement().equals(section)) {
                    section = a.statement();
                    out.append("BS".equals(section) ? "[재무상태표]" : "IS".equals(section) ? "[손익계산서]" : "[" + section + "]")
                            .append('\n');
                }
                out.append("- ").append(a.account()).append(": ").append(krwSigned(a.current()));
                if (a.previous() != null) {
                    out.append(" (전기 ").append(a.previousName() == null ? "" : a.previousName() + " ")
                            .append(krwSigned(a.previous())).append(')');
                }
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("재무제표 조회", e);
        }
    }

    @Tool(description = """
            주요국 경제 지표 발표 일정과 발표치(실제/예상/이전)를 조회한다. "이번 주 CPI 언제",
            "다가오는 경제 지표" 류의 질문에 사용한다. 기본으로 최근 3일~향후 7일을 조회한다.""")
    public String getEconomicEvents(
            @ToolParam(description = "국가 코드 (선택, 예: US)", required = false) String country,
            @ToolParam(description = "향후 조회 일수 (선택, 기본 7, 최대 30)", required = false) Integer daysAhead) {
        try {
            LocalDate today = LocalDate.now(SEOUL);
            String from = today.minusDays(3).format(YYYYMMDD);
            String to = today.plusDays(clamp(daysAhead, 7, 1, 30)).format(YYYYMMDD);
            String countryFilter = (country == null || country.isBlank()) ? null : country.strip().toUpperCase();
            List<EconomicEvent> events = repository.findEconomicEvents(countryFilter, from, to, 30);
            if (events.isEmpty()) {
                return "해당 기간(" + dashDate(from) + "~" + dashDate(to) + ")의 경제 지표 일정이 DB에 없다.";
            }
            StringBuilder out = new StringBuilder("경제 지표 일정 (").append(dashDate(from))
                    .append(" ~ ").append(dashDate(to)).append("):\n");
            for (EconomicEvent e : events) {
                out.append('[').append(dashDate(e.date())).append("] (").append(e.country())
                        .append(", 중요도 ").append(e.impact()).append(") ").append(e.event());
                if (notBlank(e.actual())) out.append(" | 실제 ").append(e.actual());
                if (notBlank(e.consensus())) out.append(" | 예상 ").append(e.consensus());
                if (notBlank(e.previous())) out.append(" | 이전 ").append(e.previous());
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            return fail("경제 지표 조회", e);
        }
    }

    /** 이름/코드 → 종목 해석. 정확 일치 우선은 리포지토리 정렬이 보장한다. */
    private Optional<StockInfo> resolve(String company) {
        if (company == null || company.isBlank()) {
            return Optional.empty();
        }
        return repository.findStocks(company.strip(), 1).stream().findFirst();
    }

    private String fail(String what, Exception e) {
        // 09 설계 7절: 도구 예외는 던지지 않고 실패를 결과로 반환
        log.warn("{} 실패: {}", what, e.getMessage());
        return "DB 조회 실패(" + what + "): " + e.getMessage();
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        return Math.clamp(value == null ? fallback : value, min, max);
    }

    /** "20260715" → "2026-07-15" (그 외 형식은 그대로). */
    private static String dashDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) {
            return String.valueOf(yyyymmdd);
        }
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-" + yyyymmdd.substring(6);
    }

    /** 모델 입력 날짜를 YYYYMMDD로 정규화 ("2026-07-01" 허용). 형식이 아니면 null(필터 없음). */
    private static String normalizeDate(String date) {
        if (date == null) {
            return null;
        }
        String digits = date.replaceAll("\\D", "");
        return digits.length() == 8 ? digits : null;
    }

    private static String comma(Long value) {
        return value == null ? "-" : String.format("%,d", value);
    }

    private static String signedComma(Long value) {
        return value == null ? "-" : String.format("%+,d", value);
    }

    private static String percent(Double rate) {
        return rate == null ? "-" : String.format("%+.2f%%", rate);
    }

    /** 원 단위 금액을 조/억 단위 한국어로 (예: 1,634조 349억원). */
    private static String krw(long won) {
        long abs = Math.abs(won);
        String sign = won < 0 ? "-" : "";
        if (abs >= 1_0000_0000_0000L) {
            return sign + String.format("%,d조 %,d억원", abs / 1_0000_0000_0000L,
                    abs % 1_0000_0000_0000L / 1_0000_0000L);
        }
        if (abs >= 1_0000_0000L) {
            return sign + String.format("%,d억원", abs / 1_0000_0000L);
        }
        return sign + String.format("%,d원", abs);
    }

    private static String krwSigned(long won) {
        return (won > 0 ? "+" : "") + krw(won);
    }

    private static String krwSigned(BigDecimal won) {
        return won == null ? "-" : krwSigned(won.longValue());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
