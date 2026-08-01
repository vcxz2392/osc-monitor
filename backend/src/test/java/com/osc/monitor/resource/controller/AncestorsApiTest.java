package com.osc.monitor.resource.controller;

import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AncestorsApiTest extends IntegrationTest {

    private static final long FIRST_CLUSTER_ID = 1L;
    private static final long FIRST_POD_ID = 10_000_001L;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("파드의 조상은 루트부터 클러스터·노드·네임스페이스 순으로 온다")
    void 파드의_조상() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/ancestors", FIRST_POD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].type", is("CLUSTER")))
                .andExpect(jsonPath("$[1].type", is("NODE")))
                .andExpect(jsonPath("$[2].type", is("NAMESPACE")))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[1].id", is(1001)))
                .andExpect(jsonPath("$[2].id", is(100001)))
                .andExpect(jsonPath("$[0].name", is("prod-cluster-01")))
                .andExpect(jsonPath("$[1].name", is("node-001")))
                .andExpect(jsonPath("$[2].name", is("ns-payment-01")));
    }

    @Test
    @DisplayName("클러스터는 조상이 없어 빈 배열이다")
    void 클러스터() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/ancestors", FIRST_CLUSTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("없는 리소스의 조상은 404 다 — 펼치기와 달리 대상이 특정된 요청이다")
    void 없는_리소스() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/ancestors", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
    }
}
