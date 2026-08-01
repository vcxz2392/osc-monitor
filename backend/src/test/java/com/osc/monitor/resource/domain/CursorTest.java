package com.osc.monitor.resource.domain;

import com.osc.monitor.support.ApiException;
import com.osc.monitor.support.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTest {

    @Test
    @DisplayName("인코딩한 커서를 그대로 복원한다")
    void 왕복() {
        Cursor cursor = new Cursor("pod-api-000417", 10_000_417L);

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    @DisplayName("이름에 구분자가 들어가도 마지막 것만 자른다")
    void 이름에_공백() {
        Cursor cursor = new Cursor("ns auth 07", 100_007L);

        assertThat(Cursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    @DisplayName("커서가 없으면 null 이다")
    void 빈_커서() {
        assertThat(Cursor.decode(null)).isNull();
        assertThat(Cursor.decode("  ")).isNull();
    }

    @Test
    @DisplayName("깨진 커서는 서버 오류가 아니라 잘못된 요청이다")
    void 깨진_커서() {
        assertThatThrownBy(() -> Cursor.decode("!!not-base64!!"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);

        assertThatThrownBy(() -> Cursor.decode(java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("id 가 숫자가 아님".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class);
    }
}
