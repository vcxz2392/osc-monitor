package com.osc.monitor.resource.controller.dto;

import java.util.List;

/**
 * @param nextCursor 다음 페이지가 있을 때만 채운다
 * @param rev        이 응답을 만든 시점의 리비전. 클라이언트는 이 값부터 델타를 받는다
 */
public record ChildrenResponse(List<ResourceResponse> items, String nextCursor, long rev) {
}
