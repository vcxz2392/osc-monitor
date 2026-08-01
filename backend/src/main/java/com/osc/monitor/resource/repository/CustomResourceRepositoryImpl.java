package com.osc.monitor.resource.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.osc.monitor.resource.domain.Cursor;
import com.osc.monitor.resource.domain.LikePattern;
import com.osc.monitor.resource.domain.ResourceStatus;
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

    /**
     * 상태 필터를 계층에 맞게 해석한다.
     *
     * <p>상위 계층의 status 는 하위를 굴려 올린 값이라, 파드가 10만이면 클러스터는 사실상 항상 ERROR 다.
     * 그 상태로 "정상만 보기"를 상위에도 적용하면 루트가 0건이 되어 화면이 통째로 비고,
     * 사용자는 정상인 파드를 볼 방법 자체를 잃는다.
     *
     * <p>그래서 <b>잎은 자기 상태로, 상위는 그 상태를 하위에 갖고 있는지로</b> 거른다.
     * 정상은 대응하는 집계 컬럼이 없어(healthy 개수를 세어 두지 않았다) 상위를 거르지 않고 경로로 남긴다.
     */
    private static String hasDescendantWith(ResourceStatus status) {
        return switch (status) {
            case ERROR -> "r.error_cnt > 0";
            case WARNING -> "r.warn_cnt > 0";
            case HEALTHY -> "1 = 1";
        };
    }

    @Override
    public List<ResourceEntity> findRoots(ResourceStatus status) {
        var sql = new StringBuilder("""
                SELECT r.*
                  FROM resource r
                 WHERE r.parent_id IS NULL
                """);
        // 루트는 항상 클러스터라 잎이 될 수 없다.
        if (status != null) {
            sql.append(" AND ").append(hasDescendantWith(status)).append('\n');
        }
        sql.append(" ORDER BY r.name, r.id");
        return entityManager.createNativeQuery(sql.toString(), ResourceEntity.class).getResultList();
    }

    /** 실행 계획 테스트가 손으로 옮겨 적지 않도록 조립을 꺼내 둔다. */
    static String childrenSql(Cursor cursor, ResourceStatus status) {
        var sql = new StringBuilder("""
                SELECT r.*
                  FROM resource r
                 WHERE r.parent_id = :parentId
                """);
        if (status != null) {
            sql.append(" AND ((r.type = :leafType AND r.status = :status)")
                    .append(" OR (r.type <> :leafType AND ").append(hasDescendantWith(status)).append("))\n");
        }
        if (cursor != null) {
            sql.append(" AND (r.name, r.id) > (:cursorName, :cursorId)\n");
        }
        return sql.append(" ORDER BY r.name, r.id").toString();
    }

    @Override
    public List<ResourceEntity> findChildren(long parentId, Cursor cursor, ResourceStatus status, int size) {
        var query = entityManager.createNativeQuery(childrenSql(cursor, status), ResourceEntity.class)
                .setParameter("parentId", parentId)
                .setMaxResults(size);
        if (status != null) {
            query.setParameter("leafType", ResourceType.POD.code()).setParameter("status", status.code());
        }
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
