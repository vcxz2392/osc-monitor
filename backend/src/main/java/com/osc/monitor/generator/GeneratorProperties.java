package com.osc.monitor.generator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.generator")
public record GeneratorProperties(
        long seed,
        int clusters,
        int nodesPerCluster,
        int namespacesPerNode,
        int podsPerNamespace,
        int batchSize
) {

    public static final long CLUSTER_BASE_ID = 1L;
    public static final long NODE_BASE_ID = 1_001L;
    public static final long NAMESPACE_BASE_ID = 100_001L;
    public static final long POD_BASE_ID = 10_000_001L;

    public int totalNodes() {
        return clusters * nodesPerCluster;
    }

    public int totalNamespaces() {
        return totalNodes() * namespacesPerNode;
    }

    public int totalPods() {
        return totalNamespaces() * podsPerNamespace;
    }

    /** 구간이 겹치면 서로 다른 리소스가 같은 id 를 받아 트리가 조용히 뒤섞인다. */
    public void validateIdRanges() {
        requireWithin("cluster", CLUSTER_BASE_ID, clusters, NODE_BASE_ID);
        requireWithin("node", NODE_BASE_ID, totalNodes(), NAMESPACE_BASE_ID);
        requireWithin("namespace", NAMESPACE_BASE_ID, totalNamespaces(), POD_BASE_ID);
    }

    private static void requireWithin(String level, long baseId, int count, long nextBaseId) {
        if (baseId + count > nextBaseId) {
            throw new IllegalStateException(
                    "%s id 구간(%d 개)이 다음 계층 시작(%d)을 넘습니다. 규모를 줄이거나 base id 를 다시 잡으세요."
                            .formatted(level, count, nextBaseId));
        }
    }
}
