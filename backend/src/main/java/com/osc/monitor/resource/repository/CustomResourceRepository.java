package com.osc.monitor.resource.repository;

import java.util.Collection;
import java.util.List;

import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.ResourceStatus;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.entity.ResourceEntity;

public interface CustomResourceRepository {

    List<ResourceEntity> findRoots(ResourceStatus status);

    List<ResourceEntity> findChildren(long parentId, Cursor cursor, ResourceStatus status, int size);

    List<ResourceEntity> search(String name, ResourceType type, int size);

    List<ResourceEntity> findChanges(long since, Long sinceId, Collection<Long> parentIds,
                                    boolean includeRoots, int size);

    void deleteAllResources();

    void insertAll(List<ResourceEntity> resources);

    void analyze();
}
