package com.osc.monitor.resource.repository;

import java.util.List;

import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.entity.ResourceEntity;

public interface CustomResourceRepository {

    List<ResourceEntity> findRoots();

    List<ResourceEntity> findChildren(long parentId, Cursor cursor, int size);

    List<ResourceEntity> search(String name, ResourceType type, int size);

    void deleteAllResources();

    void insertAll(List<ResourceEntity> resources);

    void analyze();
}
