package com.osc.monitor.resource;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 적재는 엔티티를 받되 영속성 컨텍스트를 거치지 않고 JDBC batch 로 넣는다. */
@Repository
public class ResourceRepository {

    private static final String INSERT_SQL = """
            INSERT INTO resource
                (id, parent_id, type, name, status, path, updated_at, rev,
                 error_cnt, warn_cnt, child_cnt, leaf_cnt, metrics_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public ResourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteAll() {
        jdbc.execute("TRUNCATE TABLE resource");
    }

    public void insertAll(List<ResourceEntity> resources) {
        if (resources.isEmpty()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>(resources.size());
        for (ResourceEntity resource : resources) {
            rows.add(toRow(resource));
        }
        jdbc.batchUpdate(INSERT_SQL, rows);
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
