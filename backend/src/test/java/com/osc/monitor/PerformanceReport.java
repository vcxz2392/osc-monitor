package com.osc.monitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 측정값을 파일로 남긴다.
 *
 * <p>README 의 수치를 손으로 옮겨 적으면 코드가 바뀌어도 문서가 그대로 남는다.
 * 테스트가 값을 만들고 문서는 그것을 옮기게 해서, 어긋나면 눈에 띄도록 한다.
 */
public final class PerformanceReport {

    private static final Path OUTPUT = Path.of("build/reports/perf/backend-api.md");
    private static final Map<String, String> ROWS = new LinkedHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(PerformanceReport::write));
    }

    private PerformanceReport() {
    }

    public static void record(String api, String scale, long targetMs, Measure.Result<?> result) {
        ROWS.put(api, "| `%s` | %s | %dms | **%.1fms** | %.1f | %.1f |"
                .formatted(api, scale, targetMs, result.median(), result.min(), result.max()));
    }

    private static synchronized void write() {
        if (ROWS.isEmpty()) {
            return;
        }
        StringBuilder out = new StringBuilder("""
                # 백엔드 API 응답시간

                Testcontainers MySQL 8.4 (버퍼 풀 256M) · 102,220행 · 시드 42 · warm-up 1회 버리고 5회 중위값
                재현 = `./gradlew test`

                | API | 규모 | 목표 | 중위 | 최소 | 최대 |
                | --- | --- | ---: | ---: | ---: | ---: |
                """);
        ROWS.values().forEach(row -> out.append(row).append('\n'));
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, out.toString());
        } catch (IOException e) {
            throw new IllegalStateException("성능 측정 결과를 쓰지 못했습니다: " + OUTPUT, e);
        }
    }
}
