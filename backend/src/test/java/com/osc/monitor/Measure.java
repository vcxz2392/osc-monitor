package com.osc.monitor;

import java.util.Arrays;
import java.util.function.Supplier;

/** 성능 목표를 API 단위로 확인하기 위한 최소 도구. 첫 회는 버리고 중위값을 쓴다. */
public final class Measure {

    private Measure() {
    }

    public static <T> Result<T> medianMillis(int runs, Supplier<T> action) {
        action.get(); // warm-up: JIT 와 커넥션 초기화를 측정에서 뺀다

        double[] taken = new double[runs];
        T last = null;
        for (int i = 0; i < runs; i++) {
            long startedAt = System.nanoTime();
            last = action.get();
            taken[i] = (System.nanoTime() - startedAt) / 1_000_000.0;
        }
        Arrays.sort(taken);
        return new Result<>(taken[runs / 2], taken[0], taken[runs - 1], last);
    }

    public record Result<T>(double median, double min, double max, T value) {

        @Override
        public String toString() {
            return "중위 %.1fms (최소 %.1f / 최대 %.1f)".formatted(median, min, max);
        }
    }
}
