package com.osc.monitor.resource.controller;

import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchApiTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("이름 중간 토큰으로도 찾는다 — 앞부분만 지원하면 여기서 0건이 된다")
    void 토큰_경계() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(50)))
                .andExpect(jsonPath("$.items[*].name", everyItem(startsWith("pod-api-"))));
    }

    @Test
    @DisplayName("앞부분 매칭도 그대로 동작한다")
    void 앞부분_매칭() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "ns-auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name", startsWith("ns-auth")))
                .andExpect(jsonPath("$.items[0].type", is("NAMESPACE")));
    }

    @Test
    @DisplayName("결과마다 위치를 알 수 있도록 조상 이름 사전이 함께 온다")
    void 조상_이름_사전() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "ns-auth").param("size", "3"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].path", notNullValue()))
                .andExpect(jsonPath("$.ancestorNames", aMapWithSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("종류로 범위를 좁힐 수 있다")
    void 종류_필터() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "node").param("type", "NODE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].type", everyItem(is("NODE"))));
    }

    @Test
    @DisplayName("상한에 걸리면 잘렸다고 알린다")
    void 상한() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "pod").param("size", "10"))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.truncated", is(true)));

        mockMvc.perform(get("/api/resources/search").param("q", "prod-cluster-01"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.truncated", is(false)));
    }

    @Test
    @DisplayName("와일드카드를 입력해도 전체 매칭이 되지 않는다")
    void 와일드카드_이스케이프() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/resources/search").param("q", "_"))
                .andExpect(jsonPath("$.items", hasSize(0)));

        // 이스케이프 문자 자체를 입력해도 SQL 의 ESCAPE 절과 충돌하지 않는다
        mockMvc.perform(get("/api/resources/search").param("q", "!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(get("/api/resources/search").param("q", "pod!api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("앞뒤 공백은 제거하고 찾는다")
    void 공백_제거() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "  api  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(50)));
    }

    @Test
    @DisplayName("검색어가 없으면 400 이다 — 10만 건을 정렬해 자르는 요청이 된다")
    void 검색어_없음() throws Exception {
        mockMvc.perform(get("/api/resources/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));

        mockMvc.perform(get("/api/resources/search").param("q", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("모르는 종류를 주면 400 이다")
    void 잘못된_종류() throws Exception {
        mockMvc.perform(get("/api/resources/search").param("q", "api").param("type", "SERVICE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }
}
