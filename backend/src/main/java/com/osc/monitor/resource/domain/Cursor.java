package com.osc.monitor.resource.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.osc.monitor.resource.repository.entity.ResourceEntity;
import com.osc.monitor.support.ApiException;
import com.osc.monitor.support.ErrorCode;

/** 정렬 키 (name, id) 를 감싼 불투명 커서. 정렬 기준을 바꿔도 클라이언트 계약이 유지된다. */
public record Cursor(String name, long id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final char SEPARATOR = ' ';

    public static Cursor of(ResourceEntity resource) {
        return new Cursor(resource.getName(), resource.getId());
    }

    public String encode() {
        return ENCODER.encodeToString((name + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
            // 이름에 구분자가 들어와도 마지막 것만 자른다.
            int at = raw.lastIndexOf(SEPARATOR);
            return new Cursor(raw.substring(0, at), Long.parseLong(raw.substring(at + 1)));
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new ApiException(ErrorCode.INVALID_CURSOR);
        }
    }
}
