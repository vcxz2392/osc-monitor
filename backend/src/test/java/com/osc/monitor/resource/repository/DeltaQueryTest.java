package com.osc.monitor.resource.repository;

import java.util.List;
import java.util.stream.LongStream;

import com.osc.monitor.IntegrationTest;
import com.osc.monitor.Measure;
import com.osc.monitor.PerformanceReport;
import com.osc.monitor.resource.controller.dto.ChangesRequest;
import com.osc.monitor.resource.service.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 델타 응답이 전체 데이터량이 아니라 화면 크기에 비례하는지 본다. */
class DeltaQueryTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DeltaQueryTest.class);

    private static final long FIRST_NAMESPACE_ID = 100_001L;

    /** 화면에 펼쳐 둘 만한 규모 — 클러스터 1 + 노드 10 + 네임스페이스 10. */
    private static final List<Long> OPEN_PARENTS = LongStream.concat(
            LongStream.of(1L),
            LongStream.concat(LongStream.range(1_001L, 1_011L), LongStream.range(100_001L, 100_011L))
    ).boxed().toList();

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void 리비전을_되돌린다() {
        jdbcTemplate.update("UPDATE resource SET rev = 1 WHERE rev > 1");
        jdbcTemplate.update("UPDATE revision_seq SET cur = 1 WHERE id = 1");
    }

    @Test
    @DisplayName("델타는 전체 스캔을 하지 않는다")
    void 실행계획() {
        var plan = jdbcTemplate.queryForObject("""
                EXPLAIN ANALYZE
                SELECT r.* FROM resource r
                 WHERE r.rev > 1 AND (r.parent_id IN (100001, 100002) OR r.parent_id IS NULL)
                 ORDER BY r.rev, r.id LIMIT 501
                """, String.class);

        log.info("델타 실행 계획\n{}", plan);
        assertThat(plan.toLowerCase()).doesNotContain("table scan on resource");
    }

    @Test
    @DisplayName("화면에 펼친 만큼만 내려오고 200ms 목표 안에 든다")
    void 응답시간() {
        jdbcTemplate.update("UPDATE resource SET rev = 2 WHERE parent_id = ?", FIRST_NAMESPACE_ID);
        jdbcTemplate.update("UPDATE revision_seq SET cur = 2 WHERE id = 1");

        var request = new ChangesRequest(1L, null, OPEN_PARENTS, true);
        var result = Measure.medianMillis(5, () -> resourceService.changes(request));

        PerformanceReport.record("POST /changes", "펼친 부모 21개 · 변경 50건", 200, result);
        log.info("델타(펼친 부모 {}개) {}", OPEN_PARENTS.size(), result);
        assertThat(result.value().changed()).hasSize(50);
        assertThat(result.median()).isLessThan(200);
    }
}
