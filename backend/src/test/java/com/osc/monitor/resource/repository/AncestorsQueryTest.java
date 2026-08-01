package com.osc.monitor.resource.repository;

import com.osc.monitor.IntegrationTest;
import com.osc.monitor.Measure;
import com.osc.monitor.PerformanceReport;
import com.osc.monitor.resource.service.ResourceService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조상 조회는 계층을 거슬러 올라가는 대신 경로를 파싱해 PK 로 한 번에 가져온다.
 * 그 주장은 응답시간이 아니라 <b>나가는 쿼리 수</b>로 증명된다.
 */
class AncestorsQueryTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AncestorsQueryTest.class);

    private static final long FIRST_POD_ID = 10_000_001L;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("가장 깊은 계층이어도 쿼리는 2회다 — 대상 조회 1 + 조상 IN 조회 1")
    void 쿼리_수() {
        var statistics = statistics();
        statistics.clear();

        resourceService.ancestors(FIRST_POD_ID);

        log.info("조상 조회 쿼리 수: {}", statistics.getPrepareStatementCount());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("조상 조회는 500ms 목표 안에 든다")
    void 응답시간() {
        var result = Measure.medianMillis(5, () -> resourceService.ancestors(FIRST_POD_ID));

        PerformanceReport.record("GET /{id}/ancestors", "깊이 4", 500, result);
        log.info("조상 조회(깊이 4) {}", result);
        assertThat(result.value()).hasSize(3);
        assertThat(result.median()).isLessThan(500);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
