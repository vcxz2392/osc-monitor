package com.osc.monitor.generator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.osc.monitor.resource.repository.ResourceRepository;
import com.osc.monitor.resource.domain.ResourceCounts;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.revision.repository.RevisionRepository;
import com.osc.monitor.revision.repository.entity.RevisionSeq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mock 데이터 생성기. 서비스와 분리해 최초 1회만 실행한다.
 *
 * <pre>
 *   ./gradlew bootRun --args='--app.generator.enabled=true'
 * </pre>
 */
@Component
@EnableConfigurationProperties(GeneratorProperties.class)
@ConditionalOnProperty(prefix = "app.generator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);

    /** 적재 직후 모든 행의 리비전. 클라이언트는 이 값을 기준으로 델타를 받기 시작한다. */
    private static final long INITIAL_REV = 1L;

    private static final String[] ENVS = {"prod", "stage", "dev"};
    private static final String[] REGIONS = {"ap-northeast-2", "us-east-1", "eu-west-1"};
    private static final String[] VERSIONS = {"1.29.4", "1.30.2", "1.31.0"};
    private static final String[] DOMAINS = {"payment", "order", "auth", "search", "delivery", "settle"};
    private static final String[] WORKLOADS = {"api", "web", "worker", "batch", "cache"};

    private final ResourceRepository resources;
    private final RevisionRepository revisions;
    private final GeneratorProperties props;

    @Override
    public void run(ApplicationArguments args) {
        props.validateIdRanges();

        long startedAt = System.currentTimeMillis();
        log.info("데이터 생성 시작: cluster={}, node={}, namespace={}, pod={}",
                props.clusters(), props.totalNodes(), props.totalNamespaces(), props.totalPods());

        resources.deleteAllResources();

        Random random = new Random(props.seed());
        Instant baseTime = Instant.now();

        // 잎의 상태를 먼저 만들고 부모 단위로 접어 올려 집계를 INSERT 시점에 확정한다.
        byte[] podStatuses = generatePodStatuses(random);
        Aggregate namespaces = foldLeaves(podStatuses, props.podsPerNamespace(), props.totalNamespaces());
        Aggregate nodes = fold(namespaces, props.namespacesPerNode(), props.totalNodes());
        Aggregate clusters = fold(nodes, props.nodesPerCluster(), props.clusters());

        insertClusters(clusters, random, baseTime);
        insertNodes(nodes, random, baseTime);
        insertNamespaces(namespaces, random, baseTime);
        insertPods(podStatuses, random, baseTime);

        resources.analyze();
        revisions.save(RevisionSeq.of(INITIAL_REV));

        long total = props.clusters() + props.totalNodes() + props.totalNamespaces() + props.totalPods();
        log.info("데이터 생성 완료: {}건, {}ms", total, System.currentTimeMillis() - startedAt);
    }

    private byte[] generatePodStatuses(Random random) {
        byte[] statuses = new byte[props.totalPods()];
        for (int i = 0; i < statuses.length; i++) {
            double r = random.nextDouble();
            statuses[i] = (byte) (r < 0.90 ? ResourceStatus.HEALTHY.code()
                    : r < 0.97 ? ResourceStatus.WARNING.code()
                    : ResourceStatus.ERROR.code());
        }
        return statuses;
    }

    private Aggregate foldLeaves(byte[] leafStatuses, int childrenPerParent, int parentCount) {
        int[] errors = new int[parentCount];
        int[] warns = new int[parentCount];
        for (int i = 0; i < leafStatuses.length; i++) {
            int parent = i / childrenPerParent;
            if (leafStatuses[i] == ResourceStatus.ERROR.code()) {
                errors[parent]++;
            } else if (leafStatuses[i] == ResourceStatus.WARNING.code()) {
                warns[parent]++;
            }
        }
        return new Aggregate(errors, warns);
    }

    private Aggregate fold(Aggregate child, int childrenPerParent, int parentCount) {
        int[] errors = new int[parentCount];
        int[] warns = new int[parentCount];
        for (int i = 0; i < child.errors().length; i++) {
            int parent = i / childrenPerParent;
            errors[parent] += child.errors()[i];
            warns[parent] += child.warns()[i];
        }
        return new Aggregate(errors, warns);
    }

    private void insertClusters(Aggregate agg, Random random, Instant baseTime) {
        int leafCnt = props.podsPerNamespace() * props.namespacesPerNode() * props.nodesPerCluster();
        Batch batch = new Batch();
        for (int c = 0; c < props.clusters(); c++) {
            long id = GeneratorProperties.CLUSTER_BASE_ID + c;
            batch.add(new ResourceEntity(
                    id, null, ResourceType.CLUSTER,
                    "%s-cluster-%02d".formatted(ENVS[c % ENVS.length], c + 1),
                    ResourceStatus.rollUp(agg.errors()[c], agg.warns()[c]),
                    "/%d/".formatted(id),
                    updatedAt(baseTime, random), INITIAL_REV,
                    new ResourceCounts(agg.errors()[c], agg.warns()[c], props.nodesPerCluster(), leafCnt),
                    "{\"version\":\"%s\",\"region\":\"%s\"}"
                            .formatted(VERSIONS[c % VERSIONS.length], REGIONS[c % REGIONS.length])));
        }
        batch.flush();
    }

    private void insertNodes(Aggregate agg, Random random, Instant baseTime) {
        int leafCnt = props.podsPerNamespace() * props.namespacesPerNode();
        Batch batch = new Batch();
        for (int gn = 0; gn < props.totalNodes(); gn++) {
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long id = GeneratorProperties.NODE_BASE_ID + gn;
            batch.add(new ResourceEntity(
                    id, clusterId, ResourceType.NODE,
                    "node-%03d".formatted(gn + 1),
                    ResourceStatus.rollUp(agg.errors()[gn], agg.warns()[gn]),
                    "/%d/%d/".formatted(clusterId, id),
                    updatedAt(baseTime, random), INITIAL_REV,
                    new ResourceCounts(agg.errors()[gn], agg.warns()[gn], props.namespacesPerNode(), leafCnt),
                    "{\"cpu\":%d,\"mem\":%d}".formatted(random.nextInt(101), random.nextInt(101))));
        }
        batch.flush();
    }

    private void insertNamespaces(Aggregate agg, Random random, Instant baseTime) {
        Batch batch = new Batch();
        for (int gs = 0; gs < props.totalNamespaces(); gs++) {
            int gn = gs / props.namespacesPerNode();
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long nodeId = GeneratorProperties.NODE_BASE_ID + gn;
            long id = GeneratorProperties.NAMESPACE_BASE_ID + gs;
            batch.add(new ResourceEntity(
                    id, nodeId, ResourceType.NAMESPACE,
                    "ns-%s-%02d".formatted(DOMAINS[gs % DOMAINS.length], gs % props.namespacesPerNode() + 1),
                    ResourceStatus.rollUp(agg.errors()[gs], agg.warns()[gs]),
                    "/%d/%d/%d/".formatted(clusterId, nodeId, id),
                    updatedAt(baseTime, random), INITIAL_REV,
                    new ResourceCounts(agg.errors()[gs], agg.warns()[gs],
                            props.podsPerNamespace(), props.podsPerNamespace()),
                    "{\"quotaCpu\":%d,\"quotaMem\":%d}"
                            .formatted(random.nextInt(101), random.nextInt(101))));
        }
        batch.flush();
    }

    private void insertPods(byte[] podStatuses, Random random, Instant baseTime) {
        Batch batch = new Batch();
        for (int gp = 0; gp < props.totalPods(); gp++) {
            int gs = gp / props.podsPerNamespace();
            int gn = gs / props.namespacesPerNode();
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long nodeId = GeneratorProperties.NODE_BASE_ID + gn;
            long namespaceId = GeneratorProperties.NAMESPACE_BASE_ID + gs;
            long id = GeneratorProperties.POD_BASE_ID + gp;
            batch.add(new ResourceEntity(
                    id, namespaceId, ResourceType.POD,
                    "pod-%s-%06d".formatted(WORKLOADS[gp % WORKLOADS.length], gp + 1),
                    ResourceStatus.of(podStatuses[gp]),
                    "/%d/%d/%d/%d/".formatted(clusterId, nodeId, namespaceId, id),
                    updatedAt(baseTime, random), INITIAL_REV,
                    ResourceCounts.LEAF,
                    "{\"restarts\":%d,\"ageHours\":%d}"
                            .formatted(random.nextInt(10), random.nextInt(720))));
        }
        batch.flush();
    }

    /** 전부 같은 시각이면 "마지막 갱신 시간" 칸이 의미를 잃는다. */
    private Instant updatedAt(Instant baseTime, Random random) {
        return baseTime.minusSeconds(random.nextInt(3600));
    }

    /** 10만 건을 한 리스트에 모으지 않도록 배치 크기마다 내보낸다. */
    private final class Batch {

        private final List<ResourceEntity> buffer = new ArrayList<>(props.batchSize());

        void add(ResourceEntity resource) {
            buffer.add(resource);
            if (buffer.size() >= props.batchSize()) {
                flush();
            }
        }

        void flush() {
            resources.insertAll(buffer);
            buffer.clear();
        }
    }

    private record Aggregate(int[] errors, int[] warns) {
    }
}
