package com.osc.monitor.resource.repository.entity;

import java.time.Instant;

import com.osc.monitor.resource.domain.ResourceCounts;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Slf4j
@Entity
@Getter
@Table(name = "resource")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceEntity {

    @Id
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private ResourceType type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private ResourceStatus status;

    @Column(nullable = false, length = 64)
    private String path;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private long rev;

    @Column(name = "error_cnt", nullable = false)
    private int errorCnt;

    @Column(name = "warn_cnt", nullable = false)
    private int warnCnt;

    @Column(name = "child_cnt", nullable = false)
    private int childCnt;

    @Column(name = "leaf_cnt", nullable = false)
    private int leafCnt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json")
    private String metricsJson;

    /** 잎의 상태를 바꾼다. 잎에는 집계가 없다. */
    public void changeStatus(ResourceStatus next, long rev, Instant at) {
        this.status = next;
        this.rev = rev;
        this.updatedAt = at;
    }

    /**
     * 하위 집계 증감을 반영하고 상태를 다시 도출한다.
     *
     * <p>증감을 SQL 안에서 계산하면 같은 UPDATE 문에서 방금 갱신한 컬럼을 다시 참조하게 되어
     * 상태 재계산이 중복 반영될 수 있다. 현재 값을 읽어 애플리케이션에서 확정한다.
     */
    public void applyAggregateDelta(int errorDelta, int warnDelta, long rev, Instant at) {
        this.errorCnt = clamp(this.errorCnt + errorDelta, "error");
        this.warnCnt = clamp(this.warnCnt + warnDelta, "warn");
        this.status = ResourceStatus.rollUp(this.errorCnt, this.warnCnt);
        this.rev = rev;
        this.updatedAt = at;
    }

    /** 음수가 되면 이미 집계가 어긋난 것이다. 0 으로 덮되 조용히 넘기지는 않는다. */
    private int clamp(int value, String field) {
        if (value < 0) {
            log.warn("하위 집계가 음수가 되었다. id={}, field={}, value={}", id, field, value);
            return 0;
        }
        return value;
    }

    public ResourceEntity(Long id, Long parentId, ResourceType type, String name, ResourceStatus status,
                          String path, Instant updatedAt, long rev, ResourceCounts counts, String metricsJson) {
        this.id = id;
        this.parentId = parentId;
        this.type = type;
        this.name = name;
        this.status = status;
        this.path = path;
        this.updatedAt = updatedAt;
        this.rev = rev;
        this.errorCnt = counts.error();
        this.warnCnt = counts.warn();
        this.childCnt = counts.child();
        this.leafCnt = counts.leaf();
        this.metricsJson = metricsJson;
    }
}
