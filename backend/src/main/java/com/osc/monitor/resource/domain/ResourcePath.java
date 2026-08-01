package com.osc.monitor.resource.domain;

import java.util.ArrayList;
import java.util.List;

/** 자기 자신을 포함한 조상 id 경로. 예: {@code /1/1001/100001/10000001/} */
public final class ResourcePath {

    private static final char SEPARATOR = '/';

    private ResourcePath() {
    }

    /** 자기 자신을 제외한 조상 id 를 루트부터 순서대로 돌려준다. */
    public static List<Long> parseAncestorIds(String path) {
        var ids = new ArrayList<Long>(3);
        var from = 1;
        while (true) {
            var to = path.indexOf(SEPARATOR, from);
            if (to < 0) {
                break;
            }
            ids.add(Long.parseLong(path.substring(from, to)));
            from = to + 1;
        }
        if (!ids.isEmpty()) {
            ids.removeLast();
        }
        return ids;
    }
}
