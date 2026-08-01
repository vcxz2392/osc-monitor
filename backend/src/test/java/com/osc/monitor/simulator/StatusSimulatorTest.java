package com.osc.monitor.simulator;

import java.util.List;

import com.osc.monitor.IntegrationTest;
import com.osc.monitor.generator.DataGenerator;
import com.osc.monitor.resource.controller.dto.ChangesRequest;
import com.osc.monitor.resource.service.ResourceService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 집계는 틀려도 화면에 그럴듯한 숫자가 계속 보인다. 부호를 한 번만 틀려도
 * 시간이 갈수록 어긋나는데 아무도 모른다. 그래서 불변식을 테스트로 고정한다.
 *
 * <p>스케줄러가 배경에서 도는 것을 막으려고 주기를 1시간으로 두고 직접 호출한다.
 */
@TestPropertySource(properties = {
        "app.simulator.enabled=true",
        "app.simulator.interval-ms=3600000",
        "app.simulator.mutations=50"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusSimulatorTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(StatusSimulatorTest.class);

    /** 네임스페이스의 집계가 하위 파드의 실제 분포와 다른 건수. 0 이어야 한다. */
    private static final String MISMATCHED_NAMESPACES = """
            SELECT COUNT(*) FROM resource ns
             WHERE ns.type = 2
               AND (ns.error_cnt <> (SELECT COUNT(*) FROM resource p WHERE p.parent_id = ns.id AND p.status = 2)
                 OR ns.warn_cnt  <> (SELECT COUNT(*) FROM resource p WHERE p.parent_id = ns.id AND p.status = 1))
            """;

    @Autowired
    private StatusSimulator statusSimulator;

    @Autowired
    private DataGenerator dataGenerator;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void 리비전을_되돌린다() {
        jdbcTemplate.update("UPDATE resource SET rev = 1 WHERE rev > 1");
        jdbcTemplate.update("UPDATE revision_seq SET cur = 1 WHERE id = 1");
    }

    /**
     * 시뮬레이터는 상태를 무작위로 바꾸므로 되돌릴 방법이 없다.
     * DB 는 컨텍스트 사이에 공유되니 끝나고 데이터를 다시 만들어 다른 테스트에 새지 않게 한다.
     */
    @AfterAll
    void 데이터를_다시_만든다() {
        dataGenerator.run(null);
    }

    @Test
    @DisplayName("틱을 다섯 번 돌려도 조상 집계가 하위 실제 분포와 어긋나지 않는다")
    void 집계_불변식() {
        assertThat(mismatched()).as("적재 직후").isZero();

        for (var i = 0; i < 5; i++) {
            statusSimulator.tick();
            assertThat(mismatched()).as("%d 번째 틱 이후", i + 1).isZero();
        }
    }

    @Test
    @DisplayName("한 틱이 바꾼 것들은 같은 리비전을 공유한다")
    void 틱은_리비전_하나() {
        var before = currentRev();
        statusSimulator.tick();

        var revisions = jdbcTemplate.queryForList(
                "SELECT DISTINCT rev FROM resource WHERE rev > ?", Long.class, before);

        log.info("틱 이후 새로 생긴 리비전: {}", revisions);
        assertThat(revisions).hasSize(1);
        assertThat(revisions.getFirst()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("상위만 펼쳐 둔 화면에도 변화가 보인다 — 조상까지 새 리비전을 받기 때문")
    void 상위에서도_보인다() {
        var before = currentRev();
        statusSimulator.tick();

        var changes = resourceService.changes(new ChangesRequest(before, null, List.of(), true));

        assertThat(changes.changed()).isNotEmpty();
        assertThat(changes.changed()).allMatch(resource -> resource.type().name().equals("CLUSTER"));
    }

    @Test
    @DisplayName("한 틱이 바꾸는 파드 수는 설정값과 같다")
    void 변경_건수() {
        var before = currentRev();
        statusSimulator.tick();

        var changedPods = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM resource WHERE rev > ? AND type = 3", Integer.class, before);

        assertThat(changedPods).isEqualTo(50);
    }

    /** 노드 집계는 하위 네임스페이스 집계의 합이어야 한다. */
    private static final String MISMATCHED_NODES = """
            SELECT COUNT(*) FROM resource nd
             WHERE nd.type = 1
               AND nd.error_cnt <> (SELECT COALESCE(SUM(ns.error_cnt), 0) FROM resource ns WHERE ns.parent_id = nd.id)
            """;

    private int mismatched() {
        return jdbcTemplate.queryForObject(MISMATCHED_NAMESPACES, Integer.class)
                + jdbcTemplate.queryForObject(MISMATCHED_NODES, Integer.class);
    }

    private long currentRev() {
        return jdbcTemplate.queryForObject("SELECT cur FROM revision_seq WHERE id = 1", Long.class);
    }
}
