package app.ai.finance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/**
 * finadm 금융 데이터 조회 (docs/12-finance-db-query-selection.md).
 *
 * <p>화이트리스트 방식(09 설계 5.3절): 여기 정의된 고정 SELECT만 실행한다 — 모델이 SQL을
 * 만들지 않고, 사용자 입력은 전부 바인딩 파라미터로만 들어간다. finadm은 공유 DB이므로
 * 이 클래스에 SELECT 외의 문장을 추가하지 않는다.
 *
 * <p>코드 체계(12 문서 2절 실측): 시세·수급·재무 테이블은 모두 6자리 단축코드를 쓰므로
 * 종목 해석({@link #findStocks})은 {@code info_stock.isu_srt_cd}(단축코드)로 통일한다.
 * {@code delist_yn}은 전 종목이 'Y'라 필터로 쓰지 않는다.
 */
@Repository
public class FinanceDbRepository {

    /** 종목 기본 정보 (info_stock). shortCode가 시세·수급·재무 조회의 키다. */
    public record StockInfo(String shortCode, String name, String fullName, String market,
                            String corpCode, String listedDate, Long listedShares,
                            String ceo, String industryCode) {}

    /** 일별 시세 (stock_trade). 금액 단위: 원. */
    public record PriceRow(String date, String name, long open, long high, long low, long close,
                           Double flucRate, Long volume, BigDecimal tradeValue, Long marketCap, String market) {}

    /** 순위 행 (stock_trade 최신일). */
    public record RankRow(String date, String name, String code, String market,
                          long close, Double flucRate, Long marketCap, BigDecimal tradeValue) {}

    /** 투자자별 수급 (investor_trade_feature). frgnNetValueMillions 단위: 백만원(실측 검산). */
    public record InvestorRow(String date, long close, Double flucRate, Long frgnNetValueMillions,
                              Long frgnNetQty, Long instNetQty, Long prsnNetQty,
                              String dominantActor, String marketRegime, Integer totalSignal) {}

    /** 재무제표 보고서 식별자 (dart_account). */
    public record ReportKey(String year, String reportCode, String fsDiv) {}

    /** 재무제표 계정 (dart_account). 금액 단위: 원(KRW). */
    public record AccountRow(String statement, String account,
                             BigDecimal current, String currentName,
                             BigDecimal previous, String previousName, String currency) {}

    /** 경제 지표 이벤트 (economic_events). 날짜는 YYYYMMDD. */
    public record EconomicEvent(String date, String country, String event, String category,
                                String impact, String actual, String consensus, String previous) {}

    /** 순위 기준 화이트리스트 — 여기 정의된 컬럼·방향만 SQL에 들어간다. */
    public enum RankMetric {
        MARKET_CAP("mktcap", "DESC"),
        TOP_GAINERS("fluc_rt", "DESC"),
        TOP_LOSERS("fluc_rt", "ASC"),
        TRADE_VALUE("acc_trdval", "DESC");

        final String column;
        final String direction;

        RankMetric(String column, String direction) {
            this.column = column;
            this.direction = direction;
        }
    }

    private final JdbcClient jdbc;

    public FinanceDbRepository(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** 종목명(부분 일치)·단축코드로 종목 검색. 정확 일치 → 짧은 이름 순 (예: "삼성전자" 우선주보다 먼저). */
    public List<StockInfo> findStocks(String query, int limit) {
        return jdbc.sql("""
                        SELECT isu_srt_cd, isu_abbrv, isu_nm, mkt_tp_nm, corp_code,
                               list_dd, list_shrs, ceo_nm, industry_code
                        FROM info_stock
                        WHERE isu_srt_cd = :q OR isu_abbrv LIKE '%' || :q || '%' OR isu_nm LIKE '%' || :q || '%'
                        ORDER BY CASE WHEN isu_srt_cd = :q OR isu_abbrv = :q THEN 0 ELSE 1 END,
                                 length(isu_abbrv), isu_abbrv
                        LIMIT :limit
                        """)
                .param("q", query)
                .param("limit", limit)
                .query((rs, i) -> new StockInfo(
                        rs.getString("isu_srt_cd"), rs.getString("isu_abbrv"), rs.getString("isu_nm"),
                        rs.getString("mkt_tp_nm"), rs.getString("corp_code"), rs.getString("list_dd"),
                        (Long) rs.getObject("list_shrs"), rs.getString("ceo_nm"), rs.getString("industry_code")))
                .list();
    }

    /** 일별 시세 — 기간 미지정 시 최근 rows일. 날짜는 YYYYMMDD. */
    public List<PriceRow> findPrices(String shortCode, String fromDate, String toDate, int rows) {
        return jdbc.sql("""
                        SELECT bas_dd, isu_nm, tdd_opnprc, tdd_hgprc, tdd_lwprc, tdd_clsprc,
                               fluc_rt, acc_trdvol, acc_trdval, mktcap, mkt_nm
                        FROM stock_trade
                        WHERE isu_cd = :code
                          AND (:fromDate IS NULL OR bas_dd >= :fromDate)
                          AND (:toDate IS NULL OR bas_dd <= :toDate)
                        ORDER BY bas_dd DESC
                        LIMIT :rows
                        """)
                .param("code", shortCode)
                // 널 가능 파라미터는 타입 명시 — PostgreSQL이 :x IS NULL의 타입을 못 정하는 오류 방지
                .param("fromDate", fromDate, Types.VARCHAR)
                .param("toDate", toDate, Types.VARCHAR)
                .param("rows", rows)
                .query((rs, i) -> new PriceRow(
                        rs.getString("bas_dd"), rs.getString("isu_nm"),
                        rs.getLong("tdd_opnprc"), rs.getLong("tdd_hgprc"),
                        rs.getLong("tdd_lwprc"), rs.getLong("tdd_clsprc"),
                        (Double) doubleOrNull(rs.getBigDecimal("fluc_rt")),
                        (Long) rs.getObject("acc_trdvol"), rs.getBigDecimal("acc_trdval"),
                        (Long) rs.getObject("mktcap"), rs.getString("mkt_nm")))
                .list();
    }

    /** 최신 적재일 기준 순위. market은 호출부(FinanceDbTools)에서 화이트리스트 검증 후 전달. */
    public List<RankRow> findTopStocks(RankMetric metric, String market, int limit) {
        // metric은 enum에 고정된 컬럼·방향만 — 사용자 입력이 SQL 조각으로 들어가는 경로 없음
        String sql = """
                SELECT bas_dd, isu_nm, isu_cd, mkt_nm, tdd_clsprc, fluc_rt, mktcap, acc_trdval
                FROM stock_trade
                WHERE bas_dd = (SELECT max(bas_dd) FROM stock_trade)
                  AND (:market IS NULL OR mkt_nm = :market)
                ORDER BY %s %s NULLS LAST
                LIMIT :limit
                """.formatted(metric.column, metric.direction);
        return jdbc.sql(sql)
                .param("market", market, Types.VARCHAR)
                .param("limit", limit)
                .query((rs, i) -> new RankRow(
                        rs.getString("bas_dd"), rs.getString("isu_nm"), rs.getString("isu_cd"),
                        rs.getString("mkt_nm"), rs.getLong("tdd_clsprc"),
                        doubleOrNull(rs.getBigDecimal("fluc_rt")),
                        (Long) rs.getObject("mktcap"), rs.getBigDecimal("acc_trdval")))
                .list();
    }

    /** 최근 days일 투자자별 수급. */
    public List<InvestorRow> findInvestorTrend(String shortCode, int days) {
        return jdbc.sql("""
                        SELECT bas_dd, tdd_clsprc, fluc_rt, frgn_net_val,
                               net_buy_frgn_qty, net_buy_inst_qty, net_buy_prsn_qty,
                               dominant_actor, market_regime, tot_signal
                        FROM investor_trade_feature
                        WHERE isu_cd = :code
                        ORDER BY bas_dd DESC
                        LIMIT :days
                        """)
                .param("code", shortCode)
                .param("days", days)
                .query((rs, i) -> new InvestorRow(
                        rs.getString("bas_dd"), rs.getLong("tdd_clsprc"),
                        doubleOrNull(rs.getBigDecimal("fluc_rt")),
                        (Long) rs.getObject("frgn_net_val"),
                        (Long) rs.getObject("net_buy_frgn_qty"), (Long) rs.getObject("net_buy_inst_qty"),
                        (Long) rs.getObject("net_buy_prsn_qty"),
                        rs.getString("dominant_actor"), rs.getString("market_regime"),
                        (Integer) rs.getObject("tot_signal")))
                .list();
    }

    /**
     * 종목의 최신 재무 보고서 식별. 같은 연도 안에서는 사업보고서(11011) > 3분기(11014)
     * > 반기(11012) > 1분기(11013), 연결(CFS) 우선 (12 문서 2절 실측).
     */
    public Optional<ReportKey> findLatestReport(String stockCode, String year) {
        return jdbc.sql("""
                        SELECT bsns_year, reprt_code, fs_div
                        FROM dart_account
                        WHERE stock_code = :code AND (:year IS NULL OR bsns_year = :year)
                        GROUP BY bsns_year, reprt_code, fs_div
                        ORDER BY bsns_year DESC,
                                 CASE reprt_code WHEN '11011' THEN 4 WHEN '11014' THEN 3
                                                 WHEN '11012' THEN 2 ELSE 1 END DESC,
                                 CASE fs_div WHEN 'CFS' THEN 0 ELSE 1 END
                        LIMIT 1
                        """)
                .param("code", stockCode)
                .param("year", year, Types.VARCHAR)
                .query((rs, i) -> new ReportKey(
                        rs.getString("bsns_year"), rs.getString("reprt_code"), rs.getString("fs_div")))
                .optional();
    }

    /** 선택된 보고서의 전체 계정 (BS→IS, 공시 정렬 순서). */
    public List<AccountRow> findAccounts(String stockCode, ReportKey report) {
        return jdbc.sql("""
                        SELECT sj_div, account_nm, thstrm_amount, thstrm_nm,
                               frmtrm_amount, frmtrm_nm, currency
                        FROM dart_account
                        WHERE stock_code = :code AND bsns_year = :year
                          AND reprt_code = :reprt AND fs_div = :fs
                        ORDER BY sj_div, ord
                        """)
                .param("code", stockCode)
                .param("year", report.year())
                .param("reprt", report.reportCode())
                .param("fs", report.fsDiv())
                .query((rs, i) -> new AccountRow(
                        rs.getString("sj_div"), rs.getString("account_nm"),
                        rs.getBigDecimal("thstrm_amount"), rs.getString("thstrm_nm"),
                        rs.getBigDecimal("frmtrm_amount"), rs.getString("frmtrm_nm"),
                        rs.getString("currency")))
                .list();
    }

    /** 기간 내 경제 지표 이벤트 — 날짜순, 같은 날짜는 중요도(HIGH 먼저) 순. */
    public List<EconomicEvent> findEconomicEvents(String country, String fromDate, String toDate, int limit) {
        return jdbc.sql("""
                        SELECT eventdate, country, event, category, impact, actual, consensus, previous
                        FROM economic_events
                        WHERE eventdate BETWEEN :fromDate AND :toDate
                          AND (:country IS NULL OR country = :country)
                        ORDER BY eventdate,
                                 CASE impact WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END
                        LIMIT :limit
                        """)
                .param("country", country, Types.VARCHAR)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("limit", limit)
                .query((rs, i) -> new EconomicEvent(
                        rs.getString("eventdate"), rs.getString("country"), rs.getString("event"),
                        rs.getString("category"), rs.getString("impact"), rs.getString("actual"),
                        rs.getString("consensus"), rs.getString("previous")))
                .list();
    }

    private static Double doubleOrNull(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
