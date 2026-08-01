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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
