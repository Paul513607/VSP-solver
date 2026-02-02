package org.vspsolver.util;

public class Timer {
    private long startTime;
    private long endTime;

    public Timer() {
        this.startTime = System.currentTimeMillis();
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public void stop() {
        this.endTime = System.currentTimeMillis();
    }

    public long elapsedMilliseconds() {
        return endTime - startTime;
    }

    public double elapsedSeconds() {
        return (endTime - startTime) / 1000.0;
    }

    @Override
    public String toString() {
        return "Elapsed time: " + elapsedMilliseconds() + " ms (" + elapsedSeconds() + " s)";
    }
}