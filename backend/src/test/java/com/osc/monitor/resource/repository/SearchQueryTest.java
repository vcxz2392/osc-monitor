package com.osc.monitor.resource.repository;

import java.util.regex.Pattern;

import com.osc.monitor.IntegrationTest;
import com.osc.monitor.Measure;
import com.osc.monitor.PerformanceReport;
import com.osc.monitor.resource.domain.LikePattern;
import com.osc.monitor.resource.service.ResourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 토큰 경계 매칭의 대가를 숫자로 확인한다.
 * 앞부분 매칭은 범위 스캔이지만 토큰 매칭은 인덱스를 순서대로 훑으므로,
 * 검색어의 사전 순 위치에 따라 읽는 행 수가 크게 달라진다.
 */
class SearchQueryTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryTest.class);

    /** 실행 계획 한 줄에 추정치와 실측치가 함께 있다. loops= 바로 앞의 rows= 가 실제로 읽은 행이다. */
    private static final Pattern ACTUAL_ROWS = Pattern.compile("rows=(\\d+) loops=");

    private static final String SEARCH_SQL = """
            SELECT r.* FROM resource r
             WHERE (r.name LIKE ? ESCAPE '!' OR r.name LIKE ? ESCAPE '!')
             ORDER BY r.name, r.id
             LIMIT 50
            """;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("검색은 idx_search 를 타고 정렬 비용이 없다")
    void 실행계획() {
        var plan = explain("api");

        log.info("검색 실행 계획(api)\n{}", plan);
        assertThat(plan).contains("idx_search");
        assertThat(plan.toLowerCase()).doesNotContain("filesort");
    }

    @Test
    @DisplayName("훑는 거리는 검색어의 사전 순 위치에 좌우된다 — 최악은 결과가 없는 검색어다")
    void 읽는_행_수() {
        var api = scannedRows("api");
        var worker = scannedRows("worker");
        var none = scannedRows("zzz");

        log.info("읽은 인덱스 행 — api={}, worker={}, zzz(결과없음)={}", api, worker, none);
        assertThat(api).isLessThan(worker);
        assertThat(none).isGreaterThanOrEqualTo(worker);
    }

    @Test
    @DisplayName("최악 검색어여도 300ms 목표 안에 든다")
    void 응답시간() {
        var best = Measure.medianMillis(5, () -> resourceService.search("api", null, 50));
        var worst = Measure.medianMillis(5, () -> resourceService.search("zzz", null, 50));

        PerformanceReport.record("GET /search", "결과 50건", 300, best);
        PerformanceReport.record("GET /search (최악)", "결과 0건 = 인덱스 전체 훑기", 300, worst);
        log.info("검색 best {} / worst {}", best, worst);
        assertThat(worst.median()).isLessThan(300);
    }

    /** 운영 경로와 같은 바인딩·패턴 생성을 그대로 쓴다. 테스트만 문자열을 이어 붙이면 검증 대상이 달라진다. */
    private String explain(String keyword) {
        return jdbcTemplate.queryForObject("EXPLAIN ANALYZE " + SEARCH_SQL, String.class,
                LikePattern.startsWith(keyword), LikePattern.tokenStartsWith(keyword));
    }

    private long scannedRows(String keyword) {
        var scanLine = explain(keyword).lines()
                .filter(line -> line.contains("idx_search"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("실행 계획에 idx_search 가 없다"));
        var matcher = ACTUAL_ROWS.matcher(scanLine);
        assertThat(matcher.find()).as("실측 행 수를 찾지 못했다: %s", scanLine).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
