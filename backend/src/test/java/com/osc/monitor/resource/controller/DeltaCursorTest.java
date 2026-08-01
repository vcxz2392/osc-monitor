package com.osc.monitor.resource.controller;

import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 한 번에 바뀐 것들은 리비전을 공유한다. 그 묶음이 상한 한가운데에 걸리면
 * 리비전만으로 이어받을 때 나머지가 <b>영영 오지 않는다.</b>
 * 상한을 2로 낮춰 그 상황을 강제로 만든다.
 */
@TestPropertySource(properties = "app.changes.limit=2")
class DeltaCursorTest extends IntegrationTest {

    private static final long FIRST_NAMESPACE_ID = 100_001L;
    private static final long POD_1 = 10_000_001L;
    private static final long POD_2 = 10_000_002L;
    private static final long POD_3 = 10_000_003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void 리비전을_되돌린다() {
        jdbcTemplate.update("UPDATE resource SET rev = 1 WHERE rev > 1");
        jdbcTemplate.update("UPDATE revision_seq SET cur = 1 WHERE id = 1");
    }

    @Test
    @DisplayName("같은 리비전 묶음이 상한에 걸려도 (리비전, id) 커서로 나머지를 받는다")
    void 묶음이_잘려도_유실되지_않는다() throws Exception {
        같은_리비전으로_바꾼다(2, POD_1, POD_2, POD_3);

        changes("""
                { "since": 1, "openParentIds": [%d], "includeRoots": false }""".formatted(FIRST_NAMESPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed", hasSize(2)))
                .andExpect(jsonPath("$.truncated", is(true)))
                .andExpect(jsonPath("$.maxRev", is(2)))
                .andExpect(jsonPath("$.maxId", is((int) POD_2)));

        // 돌려받은 커서로 이어 받으면 나머지 한 건이 온다
        changes("""
                { "since": 2, "sinceId": %d, "openParentIds": [%d], "includeRoots": false }"""
                .formatted(POD_2, FIRST_NAMESPACE_ID))
                .andExpect(jsonPath("$.changed", hasSize(1)))
                .andExpect(jsonPath("$.changed[0].id", is((int) POD_3)))
                .andExpect(jsonPath("$.truncated", is(false)));
    }

    @Test
    @DisplayName("리비전만으로 이어받으면 같은 리비전의 나머지를 잃는다 — 커서가 필요한 이유")
    void 리비전만으로는_유실된다() throws Exception {
        같은_리비전으로_바꾼다(2, POD_1, POD_2, POD_3);

        // sinceId 없이 리비전만 올려 보내면 POD_3 은 다시 받을 방법이 없다
        changes("""
                { "since": 2, "openParentIds": [%d], "includeRoots": false }""".formatted(FIRST_NAMESPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions changes(String body) throws Exception {
        return mockMvc.perform(post("/api/resources/changes").contentType(APPLICATION_JSON).content(body));
    }

    private void 같은_리비전으로_바꾼다(long rev, long... ids) {
        for (var id : ids) {
            jdbcTemplate.update("UPDATE resource SET rev = ? WHERE id = ?", rev, id);
        }
        jdbcTemplate.update("UPDATE revision_seq SET cur = ? WHERE id = 1", rev);
    }
}
