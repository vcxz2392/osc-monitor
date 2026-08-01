package com.osc.monitor.resource.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.LikePattern;
import com.osc.monitor.resource.domain.ResourceType;
import com.osc.monitor.resource.repository.entity.ResourceEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CustomResourceRepositoryImpl implements CustomResourceRepository {

    private static final String INSERT_SQL = """
            INSERT INTO resource
                (id, parent_id, type, name, status, path, updated_at, rev,
                 error_cnt, warn_cnt, child_cnt, leaf_cnt, metrics_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ResourceEntity> findRoots() {
        var sql = """
                SELECT r.*
                  FROM resource r
                 WHERE r.parent_id IS NULL
                 ORDER BY r.name, r.id
                """;
        return entityManager.createNativeQuery(sql, ResourceEntity.class).getResultList();
    }

    @Override
    public List<ResourceEntity> findChildren(long parentId, Cursor cursor, int size) {
        var sql = new StringBuilder("""
                SELECT r.*
                  FROM resource r
                 WHERE r.parent_id = :parentId
                """);
        if (cursor != null) {
            sql.append(" AND (r.name, r.id) > (:cursorName, :cursorId)\n");
        }
        sql.append(" ORDER BY r.name, r.id");

        var query = entityManager.createNativeQuery(sql.toString(), ResourceEntity.class)
                .setParameter("parentId", parentId)
                .setMaxResults(size);
        if (cursor != null) {
            query.setParameter("cursorName", cursor.name()).setParameter("cursorId", cursor.id());
        }
        return query.getResultList();
    }

    /**
     * 이름 시작과 하이픈 뒤 토큰 시작을 함께 본다.
     * 앞부분만 지원하면 {@code pod-api-000417} 을 {@code api} 로 찾지 못한다.
     */
    @Override
    public List<ResourceEntity> search(String name, ResourceType type, int size) {
        var sql = new StringBuilder("""
                SELECT r.*
                  FROM resource r
                 WHERE (r.name LIKE :startsWith ESCAPE '!' OR r.name LIKE :tokenStartsWith ESCAPE '!')
                """);
        if (type != null) {
            sql.append(" AND r.type = :type\n");
        }
        sql.append(" ORDER BY r.name, r.id");

        var query = entityManager.createNativeQuery(sql.toString(), ResourceEntity.class)
                .setParameter("startsWith", LikePattern.startsWith(name))
                .setParameter("tokenStartsWith", LikePattern.tokenStartsWith(name))
                .setMaxResults(size);
        if (type != null) {
            query.setParameter("type", type.code());
        }
        return query.getResultList();
    }

    @Override
    public void deleteAllResources() {
        jdbcTemplate.execute("TRUNCATE TABLE resource");
    }

    @Override
    public void insertAll(List<ResourceEntity> resources) {
        if (resources.isEmpty()) {
            return;
        }
        var rows = new ArrayList<Object[]>(resources.size());
        for (ResourceEntity resource : resources) {
            rows.add(toRow(resource));
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows);
    }

    /** 대량 적재 직후에는 통계가 낡아 옵티마이저가 인덱스를 버리고 전체 스캔을 고른다. */
    @Override
    public void analyze() {
        jdbcTemplate.execute("ANALYZE TABLE resource");
    }

    private static Object[] toRow(ResourceEntity resource) {
        return new Object[]{
                resource.getId(),
                resource.getParentId(),
                resource.getType().code(),
                resource.getName(),
                resource.getStatus().code(),
                resource.getPath(),
                Timestamp.from(resource.getUpdatedAt()),
                resource.getRev(),
                resource.getErrorCnt(),
                resource.getWarnCnt(),
                resource.getChildCnt(),
                resource.getLeafCnt(),
                resource.getMetricsJson()
        };
    }
}
