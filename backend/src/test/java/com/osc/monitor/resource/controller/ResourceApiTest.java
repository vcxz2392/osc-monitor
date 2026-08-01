package com.osc.monitor.resource.controller;

import com.osc.monitor.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceApiTest extends IntegrationTest {

    private static final long FIRST_CLUSTER_ID = 1L;
    private static final long FIRST_NODE_ID = 1_001L;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("루트는 클러스터 20개를 이름순으로 내려준다")
    void roots() throws Exception {
        mockMvc.perform(get("/api/resources/roots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(20)))
                .andExpect(jsonPath("$.items[0].type", is("CLUSTER")))
                .andExpect(jsonPath("$.items[0].parentId", nullValue()))
                .andExpect(jsonPath("$.nextCursor", nullValue()))
                .andExpect(jsonPath("$.rev", is(1)));
    }

    @Test
    @DisplayName("루트 응답에 하위 집계와 타입별 지표가 함께 온다")
    void 루트_표시정보() throws Exception {
        mockMvc.perform(get("/api/resources/roots"))
                .andExpect(jsonPath("$.items[0].leafCnt", is(5000)))
                .andExpect(jsonPath("$.items[0].childCnt", is(10)))
                .andExpect(jsonPath("$.items[0].updatedAt", notNullValue()))
                .andExpect(jsonPath("$.items[0].metrics.version", notNullValue()))
                .andExpect(jsonPath("$.items[0].metrics.region", notNullValue()));
    }

    @Test
    @DisplayName("클러스터를 펼치면 노드 10개만 온다")
    void children() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_CLUSTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.items[0].type", is("NODE")))
                .andExpect(jsonPath("$.items[0].metrics.cpu", notNullValue()))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    @DisplayName("페이지가 꽉 차면 커서를 주고, 그 커서로 이어서 받는다")
    void 커서_페이징() throws Exception {
        String cursor = mockMvc.perform(get("/api/resources/{id}/children", FIRST_CLUSTER_ID).param("size", "4"))
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.nextCursor", notNullValue()))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/resources/{id}/children", FIRST_CLUSTER_ID)
                        .param("size", "4")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(4)))
                .andExpect(jsonPath("$.items[0].name", is("node-005")));
    }

    @Test
    @DisplayName("마지막 페이지가 딱 떨어지면 커서를 주지 않는다")
    void 마지막_페이지() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_CLUSTER_ID).param("size", "10"))
                .andExpect(jsonPath("$.items", hasSize(10)))
                .andExpect(jsonPath("$.nextCursor", nullValue()));
    }

    @Test
    @DisplayName("없는 부모를 펼치면 빈 목록이다")
    void 없는_부모() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", 999_999_999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("size 범위를 벗어나면 400 이다")
    void size_검증() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_NODE_ID).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));

        mockMvc.perform(get("/api/resources/{id}/children", FIRST_NODE_ID).param("size", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("깨진 커서는 500 이 아니라 400 이다")
    void 잘못된_커서() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/children", FIRST_CLUSTER_ID).param("cursor", "!!not-base64!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_CURSOR")));
    }
}
