package com.osc.monitor.resource.controller.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param since         이 리비전 이후의 변경을 달라는 뜻
 * @param sinceId       같은 리비전 안에서 어디까지 받았는지. 한 번에 바뀐 것들은 리비전을 공유한다
 * @param openParentIds 지금 펼쳐서 보고 있는 부모 id. 응답 크기를 화면 크기에 비례하게 만든다
 * @param includeRoots  루트(클러스터)도 보고 있는지
 */
public record ChangesRequest(
        @PositiveOrZero long since,
        @Positive Long sinceId,
        @NotNull @Size(max = 1000, message = "한 번에 관찰할 수 있는 부모는 1000개까지입니다")
        List<@NotNull @Positive Long> openParentIds,
        boolean includeRoots
) {
}
