package com.osc.monitor.resource.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourcePathTest {

    @Test
    @DisplayName("자기 자신을 뺀 조상을 루트부터 순서대로 돌려준다")
    void 파드의_조상() {
        assertThat(ResourcePath.parseAncestorIds("/1/1001/100001/10000001/"))
                .containsExactly(1L, 1001L, 100001L);
    }

    @Test
    @DisplayName("루트는 조상이 없다")
    void 클러스터() {
        assertThat(ResourcePath.parseAncestorIds("/1/")).isEmpty();
    }

    @Test
    @DisplayName("경로가 슬래시로 끝나지 않으면 마지막 조각은 읽지 않는다")
    void 닫히지_않은_경로() {
        assertThat(ResourcePath.parseAncestorIds("/1/1001/100001")).containsExactly(1L);
    }

    @Test
    @DisplayName("중간 계층은 자기 위까지만 돌려준다")
    void 네임스페이스() {
        assertThat(ResourcePath.parseAncestorIds("/1/1001/100001/")).containsExactly(1L, 1001L);
    }
}
