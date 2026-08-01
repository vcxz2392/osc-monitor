package com.osc.monitor.generator;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.osc.monitor.resource.ResourceStatus;
import com.osc.monitor.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
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
public class DataGenerator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);

    private static final String INSERT_SQL = """
            INSERT INTO resource
                (id, parent_id, type, name, status, path, updated_at, rev,
                 error_cnt, warn_cnt, child_cnt, leaf_cnt, metrics_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** 적재 직후 모든 행의 리비전. 클라이언트는 이 값을 기준으로 델타를 받기 시작한다. */
    private static final long INITIAL_REV = 1L;

    private static final String[] ENVS = {"prod", "stage", "dev"};
    private static final String[] REGIONS = {"ap-northeast-2", "us-east-1", "eu-west-1"};
    private static final String[] VERSIONS = {"1.29.4", "1.30.2", "1.31.0"};
    private static final String[] DOMAINS = {"payment", "order", "auth", "search", "delivery", "settle"};
    private static final String[] WORKLOADS = {"api", "web", "worker", "batch", "cache"};

    private final JdbcTemplate jdbc;
    private final GeneratorProperties props;

    public DataGenerator(JdbcTemplate jdbc, GeneratorProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        props.validateIdRanges();

        long startedAt = System.currentTimeMillis();
        log.info("데이터 생성 시작: cluster={}, node={}, namespace={}, pod={}",
                props.clusters(), props.totalNodes(), props.totalNamespaces(), props.totalPods());

        jdbc.execute("TRUNCATE TABLE resource");

        Random random = new Random(props.seed());
        Instant baseTime = Instant.now();

        // 잎의 상태를 먼저 만들고 부모 단위로 접어 올린다.
        // 집계 컬럼을 INSERT 시점에 정확한 값으로 채워야 적재 직후부터 화면과 실제가 일치한다.
        byte[] podStatuses = generatePodStatuses(random);
        Aggregate namespaces = foldLeaves(podStatuses, props.podsPerNamespace(), props.totalNamespaces());
        Aggregate nodes = fold(namespaces, props.namespacesPerNode(), props.totalNodes());
        Aggregate clusters = fold(nodes, props.nodesPerCluster(), props.clusters());

        insertClusters(clusters, random, baseTime);
        insertNodes(nodes, random, baseTime);
        insertNamespaces(namespaces, random, baseTime);
        insertPods(podStatuses, random, baseTime);

        jdbc.update("UPDATE revision_seq SET cur = ? WHERE id = 1", INITIAL_REV);

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
        List<Object[]> batch = new ArrayList<>(props.clusters());
        for (int c = 0; c < props.clusters(); c++) {
            long id = GeneratorProperties.CLUSTER_BASE_ID + c;
            String metrics = "{\"version\":\"%s\",\"region\":\"%s\"}"
                    .formatted(VERSIONS[c % VERSIONS.length], REGIONS[c % REGIONS.length]);
            batch.add(row(id, null, ResourceType.CLUSTER,
                    "%s-cluster-%02d".formatted(ENVS[c % ENVS.length], c + 1),
                    ResourceStatus.rollUp(agg.errors()[c], agg.warns()[c]),
                    "/%d/".formatted(id), baseTime, random,
                    agg.errors()[c], agg.warns()[c], props.nodesPerCluster(), leafCnt, metrics));
        }
        flush(batch);
    }

    private void insertNodes(Aggregate agg, Random random, Instant baseTime) {
        int leafCnt = props.podsPerNamespace() * props.namespacesPerNode();
        List<Object[]> batch = new ArrayList<>(props.batchSize());
        for (int gn = 0; gn < props.totalNodes(); gn++) {
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long id = GeneratorProperties.NODE_BASE_ID + gn;
            String metrics = "{\"cpu\":%d,\"mem\":%d}".formatted(random.nextInt(101), random.nextInt(101));
            batch.add(row(id, clusterId, ResourceType.NODE,
                    "node-%03d".formatted(gn + 1),
                    ResourceStatus.rollUp(agg.errors()[gn], agg.warns()[gn]),
                    "/%d/%d/".formatted(clusterId, id), baseTime, random,
                    agg.errors()[gn], agg.warns()[gn], props.namespacesPerNode(), leafCnt, metrics));
            flushIfFull(batch);
        }
        flush(batch);
    }

    private void insertNamespaces(Aggregate agg, Random random, Instant baseTime) {
        List<Object[]> batch = new ArrayList<>(props.batchSize());
        for (int gs = 0; gs < props.totalNamespaces(); gs++) {
            int gn = gs / props.namespacesPerNode();
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long nodeId = GeneratorProperties.NODE_BASE_ID + gn;
            long id = GeneratorProperties.NAMESPACE_BASE_ID + gs;
            String metrics = "{\"quotaCpu\":%d,\"quotaMem\":%d}"
                    .formatted(random.nextInt(101), random.nextInt(101));
            batch.add(row(id, nodeId, ResourceType.NAMESPACE,
                    "ns-%s-%02d".formatted(DOMAINS[gs % DOMAINS.length], gs % props.namespacesPerNode() + 1),
                    ResourceStatus.rollUp(agg.errors()[gs], agg.warns()[gs]),
                    "/%d/%d/%d/".formatted(clusterId, nodeId, id), baseTime, random,
                    agg.errors()[gs], agg.warns()[gs], props.podsPerNamespace(), props.podsPerNamespace(),
                    metrics));
            flushIfFull(batch);
        }
        flush(batch);
    }

    private void insertPods(byte[] podStatuses, Random random, Instant baseTime) {
        List<Object[]> batch = new ArrayList<>(props.batchSize());
        for (int gp = 0; gp < props.totalPods(); gp++) {
            int gs = gp / props.podsPerNamespace();
            int gn = gs / props.namespacesPerNode();
            long clusterId = GeneratorProperties.CLUSTER_BASE_ID + gn / props.nodesPerCluster();
            long nodeId = GeneratorProperties.NODE_BASE_ID + gn;
            long namespaceId = GeneratorProperties.NAMESPACE_BASE_ID + gs;
            long id = GeneratorProperties.POD_BASE_ID + gp;
            String metrics = "{\"restarts\":%d,\"ageHours\":%d}"
                    .formatted(random.nextInt(10), random.nextInt(720));
            batch.add(row(id, namespaceId, ResourceType.POD,
                    "pod-%s-%06d".formatted(WORKLOADS[gp % WORKLOADS.length], gp + 1),
                    ResourceStatus.of(podStatuses[gp]),
                    "/%d/%d/%d/%d/".formatted(clusterId, nodeId, namespaceId, id), baseTime, random,
                    0, 0, 0, 0, metrics));
            flushIfFull(batch);
        }
        flush(batch);
    }

    private Object[] row(long id, Long parentId, ResourceType type, String name, ResourceStatus status,
                         String path, Instant baseTime, Random random,
                         int errorCnt, int warnCnt, int childCnt, int leafCnt, String metrics) {
        Timestamp updatedAt = Timestamp.from(baseTime.minusSeconds(random.nextInt(3600)));
        return new Object[]{
                id, parentId, type.code(), name, status.code(), path,
                updatedAt, INITIAL_REV, errorCnt, warnCnt, childCnt, leafCnt, metrics
        };
    }

    private void flushIfFull(List<Object[]> batch) {
        if (batch.size() >= props.batchSize()) {
            flush(batch);
        }
    }

    private void flush(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_SQL, batch);
        batch.clear();
    }

    private record Aggregate(int[] errors, int[] warns) {
    }
}
