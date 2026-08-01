package com.osc.monitor.resource.domain;

/** 부모가 들고 있는 하위 집계. leaf 는 하위 파드 총수로 오류·경고의 분모다. */
public record ResourceCounts(int error, int warn, int child, int leaf) {

    public static final ResourceCounts LEAF = new ResourceCounts(0, 0, 0, 0);
}
