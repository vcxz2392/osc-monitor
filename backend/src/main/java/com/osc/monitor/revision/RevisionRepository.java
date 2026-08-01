package com.osc.monitor.revision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 전역 단조 증가 리비전. 한 행만 존재한다. */
@Repository
public class RevisionRepository {

    private static final int SINGLETON_ID = 1;

    private final JdbcTemplate jdbc;

    public RevisionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void reset(long value) {
        jdbc.update("UPDATE revision_seq SET cur = ? WHERE id = ?", value, SINGLETON_ID);
    }
}
