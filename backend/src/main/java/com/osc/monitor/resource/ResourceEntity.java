package com.osc.monitor.resource;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "resource")
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

    protected ResourceEntity() {
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

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public ResourceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public String getPath() {
        return path;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getRev() {
        return rev;
    }

    public int getErrorCnt() {
        return errorCnt;
    }

    public int getWarnCnt() {
        return warnCnt;
    }

    public int getChildCnt() {
        return childCnt;
    }

    public int getLeafCnt() {
        return leafCnt;
    }

    public String getMetricsJson() {
        return metricsJson;
    }
}
