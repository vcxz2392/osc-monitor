package com.osc.monitor.resource.controller;

import java.util.ArrayList;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 필터의 의미가 계층마다 다르다는 것을 고정한다.
 * 상위에도 자기 status 를 그대로 적용하면 "정상만 보기" 에서 화면이 통째로 빈다.
 */
class StatusFilterApiTest extends IntegrationTest {

    private static final long FIRST_NAMESPACE_ID = 100_001L;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("정상만 보기에서도 루트가 사라지지 않는다 — 상위는 경로로 남는다")
    void 정상_필터에_루트가_남는다() throws Exception {
        mockMvc.perform(get("/api/resources/roots").param("status", "HEALTHY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)));
    }

    @Test
    @DisplayName("파드는 자기 상태로 걸러진다")
    void 잎은_자기_상태() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_NAMESPACE_ID).param("status", "HEALTHY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status", everyItem(is("HEALTHY"))));

        mockMvc.perform(get("/api/resources/{id}/children", FIRST_NAMESPACE_ID).param("status", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].status", everyItem(is("ERROR"))));
    }

    @Test
    @DisplayName("상위는 그 상태를 하위에 가졌는지로 걸러진다")
    void 상위는_집계로() throws Exception {
        mockMvc.perform(get("/api/resources/roots").param("status", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].errorCnt", everyItem(greaterThan(0))));
    }

    @Test
    @DisplayName("필터가 없으면 전부 온다")
    void 필터_없음() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_NAMESPACE_ID))
                .andExpect(jsonPath("$.items", hasSize(50)));
    }

    @Test
    @DisplayName("필터와 커서를 함께 써도 중복·누락 없이 이어받는다")
    void 필터와_커서() throws Exception {
        var all = healthyPodIds(null, 50);
        assertThat(all).hasSizeGreaterThan(10);

        var firstPage = healthyPodIds(null, 4);
        var cursor = nextCursor(4);
        var rest = healthyPodIds(cursor, 50);

        assertThat(firstPage).hasSize(4);
        assertThat(firstPage).doesNotContainAnyElementsOf(rest);
        assertThat(concat(firstPage, rest)).containsExactlyElementsOf(all);
    }

    private List<Integer> healthyPodIds(String cursor, int size) throws Exception {
        var request = get("/api/resources/{id}/children", FIRST_NAMESPACE_ID)
                .param("status", "HEALTHY").param("size", String.valueOf(size));
        if (cursor != null) {
            request = request.param("cursor", cursor);
        }
        var body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].id");
    }

    private String nextCursor(int size) throws Exception {
        var body = mockMvc.perform(get("/api/resources/{id}/children", FIRST_NAMESPACE_ID)
                        .param("status", "HEALTHY").param("size", String.valueOf(size)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.nextCursor");
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        var all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    @Test
    @DisplayName("모르는 상태를 주면 400 이다")
    void 잘못된_상태() throws Exception {
        mockMvc.perform(get("/api/resources/roots").param("status", "DEGRADED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }
}
