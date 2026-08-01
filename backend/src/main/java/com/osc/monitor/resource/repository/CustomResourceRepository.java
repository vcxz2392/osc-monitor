package com.osc.monitor.resource.repository;

import java.util.List;

import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.repository.entity.ResourceEntity;

public interface CustomResourceRepository {

    List<ResourceEntity> findRoots();

    List<ResourceEntity> findChildren(long parentId, Cursor cursor, int size);

    void deleteAllResources();

    void insertAll(List<ResourceEntity> resources);

    void analyze();
}
