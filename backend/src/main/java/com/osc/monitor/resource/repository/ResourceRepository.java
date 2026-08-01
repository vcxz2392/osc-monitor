package com.osc.monitor.resource.repository;

import com.osc.monitor.resource.repository.entity.ResourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceRepository extends JpaRepository<ResourceEntity, Long>, CustomResourceRepository {
}
