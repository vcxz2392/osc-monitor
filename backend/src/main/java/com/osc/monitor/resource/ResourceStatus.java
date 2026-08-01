package com.osc.monitor.resource;

public enum ResourceStatus {

    HEALTHY(0),
    WARNING(1),
    ERROR(2);

    private static final ResourceStatus[] BY_CODE = values();

    private final int code;

    ResourceStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ResourceStatus of(int code) {
        if (code < 0 || code >= BY_CODE.length) {
            throw new IllegalArgumentException("알 수 없는 상태 코드: " + code);
        }
        return BY_CODE[code];
    }

    /** 하위 집계로부터 상위 계층의 상태를 도출한다. */
    public static ResourceStatus rollUp(int errorCnt, int warnCnt) {
        if (errorCnt > 0) {
            return ERROR;
        }
        return warnCnt > 0 ? WARNING : HEALTHY;
    }
}
