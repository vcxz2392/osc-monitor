package com.osc.monitor.resource.domain;

public enum ResourceType {

    CLUSTER(0),
    NODE(1),
    NAMESPACE(2),
    POD(3);

    private static final ResourceType[] BY_CODE = values();

    private final int code;

    ResourceType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ResourceType of(int code) {
        if (code < 0 || code >= BY_CODE.length) {
            throw new IllegalArgumentException("알 수 없는 리소스 종류 코드: " + code);
        }
        return BY_CODE[code];
    }
}
