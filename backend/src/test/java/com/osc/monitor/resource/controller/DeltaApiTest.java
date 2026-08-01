package com.osc.monitor.resource.controller;

import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시뮬레이터 없이 리비전을 직접 올려 경계를 만든다.
 * 각 테스트 뒤에 원래 상태(모든 행 rev=1)로 되돌려 다른 테스트에 영향을 주지 않는다.
 */
class DeltaApiTest extends IntegrationTest {

    private static final long FIRST_NAMESPACE_ID = 100_001L;
    private static final long FIRST_POD_ID = 10_000_001L;

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
    @DisplayName("바뀐 것이 없으면 빈 응답과 현재 리비전만 온다")
    void 변경_없음() throws Exception {
        changes("""
                { "since": 1, "openParentIds": [%d], "includeRoots": true }""".formatted(FIRST_NAMESPACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed", hasSize(0)))
                .andExpect(jsonPath("$.maxRev", is(1)))
                .andExpect(jsonPath("$.truncated", is(false)))
                .andExpect(jsonPath("$.maxId", nullValue()));
    }

    @Test
    @DisplayName("보고 있는 부모 아래의 변경만 내려온다")
    void 화면_범위() throws Exception {
        bumpRev(FIRST_POD_ID, 2);                       // 보고 있는 네임스페이스 아래
        bumpRev(10_000_500L, 3);                        // 다른 네임스페이스 아래

        changes("""
                { "since": 1, "openParentIds": [%d], "includeRoots": false }""".formatted(FIRST_NAMESPACE_ID))
                .andExpect(jsonPath("$.changed", hasSize(1)))
                .andExpect(jsonPath("$.changed[0].id", is((int) FIRST_POD_ID)));
    }

    @Test
    @DisplayName("보고 있는 것이 하나도 없으면 조회하지 않는다")
    void 빈_범위() throws Exception {
        bumpRev(FIRST_POD_ID, 2);

        changes("""
                { "since": 0, "openParentIds": [], "includeRoots": false }""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed", hasSize(0)));
    }

    @Test
    @DisplayName("루트를 보고 있으면 클러스터 변경도 내려온다")
    void 루트_포함() throws Exception {
        bumpRev(1L, 2);

        changes("""
                { "since": 1, "openParentIds": [], "includeRoots": true }""")
                .andExpect(jsonPath("$.changed", hasSize(1)))
                .andExpect(jsonPath("$.changed[0].type", is("CLUSTER")));
    }

    @Test
    @DisplayName("리비전 순서로 내려오고 받은 리비전 이후만 온다")
    void 리비전_이어받기() throws Exception {
        bumpRev(FIRST_POD_ID, 2);
        bumpRev(FIRST_POD_ID + 1, 3);

        changes("""
                { "since": 2, "openParentIds": [%d], "includeRoots": false }""".formatted(FIRST_NAMESPACE_ID))
                .andExpect(jsonPath("$.changed", hasSize(1)))
                .andExpect(jsonPath("$.changed[0].rev", is(3)));
    }

    @Test
    @DisplayName("본문 검증 실패는 500 이 아니라 400 이다")
    void 잘못된_요청() throws Exception {
        changes("""
                { "since": -1, "openParentIds": [], "includeRoots": true }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));

        changes("""
                { "since": 1, "includeRoots": true }""")
                .andExpect(status().isBadRequest());

        // 목록에 null 이 섞이면 바인딩 결과가 DB 마다 달라진다
        changes("""
                { "since": 1, "openParentIds": [1, null], "includeRoots": false }""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    private org.springframework.test.web.servlet.ResultActions changes(String body) throws Exception {
        return mockMvc.perform(post("/api/resources/changes").contentType(APPLICATION_JSON).content(body));
    }

    private void bumpRev(long id, long rev) {
        jdbcTemplate.update("UPDATE resource SET rev = ? WHERE id = ?", rev, id);
        jdbcTemplate.update("UPDATE revision_seq SET cur = GREATEST(cur, ?) WHERE id = 1", rev);
    }
}
