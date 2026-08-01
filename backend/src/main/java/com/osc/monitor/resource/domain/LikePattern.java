package com.osc.monitor.resource.domain;

/**
 * LIKE 패턴을 만든다.
 *
 * <p>사용자 입력의 {@code %}, {@code _} 를 이스케이프하지 않으면 와일드카드로 해석되어
 * 전혀 다른 것을 찾는다. 역슬래시는 JPQL·SQL 양쪽에서 해석이 갈려 이스케이프 문자로 {@code !} 를 쓴다.
 */
public final class LikePattern {

    public static final char ESCAPE = '!';

    private LikePattern() {
    }

    /** 이름 맨 앞에서 시작하는 매칭: {@code api} → {@code api%} */
    public static String startsWith(String raw) {
        return escape(raw) + "%";
    }

    /** 하이픈 뒤 토큰에서 시작하는 매칭: {@code api} → {@code %-api%} */
    public static String tokenStartsWith(String raw) {
        return "%-" + escape(raw) + "%";
    }

    private static String escape(String raw) {
        var escaped = new StringBuilder(raw.length() + 8);
        for (var c : raw.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                escaped.append(ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
