package com.osc.monitor.resource.controller.dto;

import java.util.List;

/**
 * @param maxRev    다음 요청에 그대로 넣을 리비전
 * @param maxId     잘렸을 때만 채운다. 같은 리비전 안에서 어디까지 받았는지
 * @param truncated 상한에 걸렸는지. true 면 클라이언트가 즉시 한 번 더 요청한다
 */
public record ChangesResponse(long maxRev, Long maxId, List<ResourceResponse> changed, boolean truncated) {
}
