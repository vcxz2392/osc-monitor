package com.osc.monitor.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.osc.monitor.resource.domain.ResourcePath;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.ResourceRepository;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.revision.repository.RevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태 변화 시뮬레이터. 요구사항의 "데이터 갱신"을 시연하려면 변화를 만들어내는 장치가 필요하다.
 *
 * <p>파드 상태를 바꾸면서 경로에 적힌 조상의 집계도 같이 갱신한다.
 * 조상까지 새 리비전을 받아야 상위 계층만 펼쳐 둔 화면에서도 변화가 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SimulatorProperties.class)
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
public class StatusSimulator {

    private final ResourceRepository resourceRepository;
    private final RevisionRepository revisionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SimulatorProperties simulatorProperties;
    private final Random random = new Random();

    private long minPodId = -1;
    private long maxPodId = -1;

    /**
     * 리비전 발급과 행 갱신은 <b>반드시 한 트랜잭션</b>이어야 한다.
     * 리비전만 먼저 커밋되면 클라이언트가 빈 응답과 함께 새 리비전을 받아 since 를 올려버리고,
     * 뒤늦게 커밋된 행은 영영 내려가지 않는다.
     */
    @Scheduled(fixedDelayString = "${app.simulator.interval-ms:2000}",
            initialDelayString = "${app.simulator.interval-ms:2000}")
    @Transactional
    public void tick() {
        if (!resolvePodIdRange()) {
            return;
        }
        // 리비전을 가장 먼저 발급한다. 이 행의 락이 곧 틱 단위 직렬화 지점이 되어
        // 인스턴스가 여럿이어도 두 틱이 같은 파드를 동시에 읽고 고치는 일이 없다.
        var rev = revisionRepository.issueNext();

        var pods = resourceRepository.findAllById(randomPodIds());
        if (pods.isEmpty()) {
            return;
        }
        var now = Instant.now();
        var ancestorDeltas = new HashMap<Long, int[]>();

        for (var pod : pods) {
            var before = pod.getStatus();
            var after = nextStatus(before);
            pod.changeStatus(after, rev, now);
            accumulate(ancestorDeltas, pod, before, after);
        }
        for (var ancestor : resourceRepository.findAllById(ancestorDeltas.keySet())) {
            var delta = ancestorDeltas.get(ancestor.getId());
            ancestor.applyAggregateDelta(delta[0], delta[1], rev, now);
        }

        log.debug("상태 변경 {}건 반영, rev={}", pods.size(), rev);
    }

    private void accumulate(Map<Long, int[]> deltas, ResourceEntity pod,
                            ResourceStatus before, ResourceStatus after) {
        var errorDelta = count(after, ResourceStatus.ERROR) - count(before, ResourceStatus.ERROR);
        var warnDelta = count(after, ResourceStatus.WARNING) - count(before, ResourceStatus.WARNING);
        if (errorDelta == 0 && warnDelta == 0) {
            return;
        }
        for (var ancestorId : ResourcePath.parseAncestorIds(pod.getPath())) {
            var accumulated = deltas.computeIfAbsent(ancestorId, key -> new int[2]);
            accumulated[0] += errorDelta;
            accumulated[1] += warnDelta;
        }
    }

    /** 파드 id 는 연속으로 할당되므로 범위만 알면 무작위 선택이 된다. ORDER BY RAND() 는 전체 스캔이라 쓰지 않는다. */
    private boolean resolvePodIdRange() {
        if (minPodId > 0) {
            return true;
        }
        var min = jdbcTemplate.queryForObject(
                "SELECT MIN(id) FROM resource WHERE type = ?", Long.class, ResourceType.POD.code());
        var max = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM resource WHERE type = ?", Long.class, ResourceType.POD.code());
        if (min == null || max == null) {
            return false;
        }
        minPodId = min;
        maxPodId = max;
        return true;
    }

    /** 중복을 허용하면 실제 변경 건수가 설정값보다 적어진다. */
    private List<Long> randomPodIds() {
        var ids = new LinkedHashSet<Long>();
        var attempts = 0;
        while (ids.size() < simulatorProperties.mutations() && attempts < simulatorProperties.mutations() * 4) {
            ids.add(minPodId + (long) (random.nextDouble() * (maxPodId - minPodId + 1)));
            attempts++;
        }
        return new ArrayList<>(ids);
    }

    /** 같은 상태로 바뀌면 변화가 없어 델타에 잡히지 않는다. 한 칸 돌려 반드시 바뀌게 한다. */
    private ResourceStatus nextStatus(ResourceStatus before) {
        var r = random.nextDouble();
        var next = r < 0.80 ? ResourceStatus.HEALTHY
                : r < 0.94 ? ResourceStatus.WARNING
                : ResourceStatus.ERROR;
        if (next == before) {
            var all = ResourceStatus.values();
            next = all[(before.ordinal() + 1) % all.length];
        }
        return next;
    }

    private static int count(ResourceStatus status, ResourceStatus target) {
        return status == target ? 1 : 0;
    }
}
