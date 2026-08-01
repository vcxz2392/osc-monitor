package com.osc.monitor.resource.repository;

import com.osc.monitor.IntegrationTest;
import com.osc.monitor.resource.service.ResourceService;
import com.osc.monitor.Measure;
import com.osc.monitor.PerformanceReport;
import com.osc.monitor.resource.controller.dto.ChildrenResponse;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 펼치기 쿼리가 목표 안에 드는지, 그리고 그게 인덱스 덕인지 확인한다.
 * 응답시간만 재면 왜 빠른지 알 수 없어 실행 계획을 함께 본다.
 */
class ChildrenQueryTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChildrenQueryTest.class);

    /** 파드 50개를 가진 네임스페이스. 가장 깊고 형제가 많은 자리다. */
    private static final long NAMESPACE_ID = 100_001L;

    private static final String CHILDREN_SQL = """
            SELECT %s id, parent_id, type, name, status, path, updated_at, rev,
                   error_cnt, warn_cnt, child_cnt, leaf_cnt, metrics_json
              FROM resource %s
             WHERE parent_id = %d
             ORDER BY name, id
             LIMIT 100
            """;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("펼치기는 100ms 목표 안에 든다")
    void 펼치기_응답시간() {
        Measure.Result<ChildrenResponse> result =
                Measure.medianMillis(5, () -> resourceService.children(NAMESPACE_ID, null, null, 100));

        PerformanceReport.record("GET /{id}/children", "파드 50건", 100, result);
        log.info("펼치기(파드 50건) {}", result);
        assertThat(result.value().items()).hasSize(50);
        assertThat(result.median()).isLessThan(100);
    }

    @Test
    @DisplayName("상태 필터를 걸어도 uk_children 으로 부모를 찾은 뒤 걸러낸다")
    void 필터_실행계획() {
        // 저장소가 실제로 조립하는 SQL 을 그대로 쓴다. 손으로 옮겨 적으면 조건이 바뀌어도 테스트가 통과한다.
        var plan = (String) entityManager
                .createNativeQuery("EXPLAIN ANALYZE " + CustomResourceRepositoryImpl.childrenSql(null, ResourceStatus.ERROR))
                .setParameter("parentId", NAMESPACE_ID)
                .setParameter("leafType", ResourceType.POD.code())
                .setParameter("status", ResourceStatus.ERROR.code())
                .getSingleResult();

        log.info("상태 필터 실행 계획\n{}", plan);
        assertThat(plan).contains("uk_children");
        assertThat(plan.toLowerCase()).doesNotContain("table scan");
    }

    @Test
    @DisplayName("상태 필터를 걸어도 200ms 목표 안에 든다")
    void 필터_응답시간() {
        var result = Measure.medianMillis(5,
                () -> resourceService.children(NAMESPACE_ID, null, ResourceStatus.ERROR, 100));

        PerformanceReport.record("GET /{id}/children?status=", "파드 50건 중 오류만", 200, result);
        log.info("상태 필터 {}", result);
        assertThat(result.median()).isLessThan(200);
    }

    @Test
    @DisplayName("루트 조회는 500ms 목표 안에 든다")
    void 루트_응답시간() {
        Measure.Result<ChildrenResponse> result = Measure.medianMillis(5, () -> resourceService.roots(null));

        PerformanceReport.record("GET /roots", "클러스터 20건", 500, result);
        log.info("루트(클러스터 20건) {}", result);
        assertThat(result.value().items()).hasSize(20);
        assertThat(result.median()).isLessThan(500);
    }

    @Test
    @DisplayName("펼치기는 uk_children 범위 스캔을 타고 filesort 가 붙지 않는다")
    void 펼치기_실행계획() {
        String plan = explain(CHILDREN_SQL.formatted("", "", NAMESPACE_ID));

        log.info("실행 계획\n{}", plan);
        assertThat(plan).contains("uk_children");
        assertThat(plan.toLowerCase()).doesNotContain("filesort");
    }

    @Test
    @DisplayName("인덱스를 무시하면 전체 스캔이 된다 — 빠른 이유가 인덱스임을 대조로 확인한다")
    void 인덱스가_없다면() {
        String withIndex = explain(CHILDREN_SQL.formatted("", "", NAMESPACE_ID));
        String withoutIndex = explain(CHILDREN_SQL.formatted("", "IGNORE INDEX (uk_children)", NAMESPACE_ID));

        log.info("인덱스 무시\n{}", withoutIndex);
        assertThat(withIndex).contains("uk_children");
        assertThat(withoutIndex).doesNotContain("uk_children");
    }

    private String explain(String sql) {
        return jdbcTemplate.queryForObject("EXPLAIN ANALYZE " + sql, String.class);
    }
}
