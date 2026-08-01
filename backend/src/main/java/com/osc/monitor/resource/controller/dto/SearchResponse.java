package com.osc.monitor.resource.controller.dto;

import java.util.List;
import java.util.Map;

/**
 * @param truncated     상한에 걸려 잘렸는지. 화면이 "더 좁히라"고 안내한다
 * @param ancestorNames 조상 id → 이름. 결과마다 경로를 붙이지 않고 사전을 한 번만 내려보낸다
 */
public record SearchResponse(List<ResourceResponse> items, boolean truncated, Map<Long, String> ancestorNames) {
}
