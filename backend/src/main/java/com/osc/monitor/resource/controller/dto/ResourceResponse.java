package com.osc.monitor.resource.controller.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.entity.ResourceEntity;

public record ResourceResponse(
        long id,
        Long parentId,
        ResourceType type,
        String name,
        ResourceStatus status,
        String path,
        Instant updatedAt,
        long rev,
        int errorCnt,
        int warnCnt,
        int childCnt,
        int leafCnt,
        JsonNode metrics
) {

    public static ResourceResponse of(ResourceEntity entity, JsonNode metrics) {
        return new ResourceResponse(
                entity.getId(), entity.getParentId(), entity.getType(), entity.getName(), entity.getStatus(),
                entity.getPath(), entity.getUpdatedAt(), entity.getRev(),
                entity.getErrorCnt(), entity.getWarnCnt(), entity.getChildCnt(), entity.getLeafCnt(), metrics);
    }
}
