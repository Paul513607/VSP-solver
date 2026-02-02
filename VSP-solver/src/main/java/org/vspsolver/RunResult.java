package org.vspsolver;

public final class RunResult {
    public final long timeMs;
    public final int sepSize;
    public final int leftSize;
    public final int rightSize;
    public final boolean valid;
    public final long bestTimeMs;
    public final String error;

    private RunResult(long timeMs, int sepSize, int leftSize, int rightSize, boolean valid, long bestTimeMs, String error) {
        this.timeMs = timeMs;
        this.sepSize = sepSize;
        this.leftSize = leftSize;
        this.rightSize = rightSize;
        this.valid = valid;
        this.bestTimeMs = bestTimeMs;
        this.error = error;
    }

    public static RunResult ok(long timeMs, int sepSize, int leftSize, int rightSize, boolean valid, long bestTimeMs) {
        return new RunResult(timeMs, sepSize, leftSize, rightSize, valid, bestTimeMs, null);
    }

    public static RunResult fail(long timeMs, String error) {
        return new RunResult(timeMs, -1, -1, -1, false, -1, error);
    }
}
