package com.osc.monitor.resource.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    @Test
    @DisplayName("앞부분 매칭과 토큰 매칭 패턴을 만든다")
    void 패턴() {
        assertThat(LikePattern.startsWith("api")).isEqualTo("api%");
        assertThat(LikePattern.tokenStartsWith("api")).isEqualTo("%-api%");
    }

    @Test
    @DisplayName("와일드카드를 이스케이프한다 — 안 하면 사용자가 전체 매칭을 만들 수 있다")
    void 와일드카드() {
        assertThat(LikePattern.startsWith("%")).isEqualTo("!%%");
        assertThat(LikePattern.startsWith("a_b")).isEqualTo("a!_b%");
        assertThat(LikePattern.tokenStartsWith("%")).isEqualTo("%-!%%");
    }

    @Test
    @DisplayName("이스케이프 문자 자체도 이스케이프한다")
    void 이스케이프_문자() {
        assertThat(LikePattern.startsWith("!")).isEqualTo("!!%");
    }
}
