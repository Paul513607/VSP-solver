package org.vspsolver.prvsp;

import java.util.*;

public class PairSet {
    private final int maxSize;
    private final boolean[][] active;
    private final List<int[]> pairs;

    public PairSet(int maxSize) {
        this.maxSize = maxSize;
        this.active = new boolean[maxSize][maxSize];
        this.pairs = new ArrayList<>(maxSize * (maxSize - 1) / 2);
        initAll();
    }

    public void initAll() {
        pairs.clear();
        for (int i = 0; i < maxSize; i++) {
            for (int j = 0; j < maxSize; j++) {
                active[i][j] = false;
            }
        }

        for (int i = 0; i < maxSize; i++) {
            for (int j = i + 1; j < maxSize; j++) {
                active[i][j] = true;
                pairs.add(new int[]{i, j});
            }
        }
    }

    public boolean isEmpty() {
        for (int i = 0; i < maxSize; i++) {
            for (int j = i + 1; j < maxSize; j++) {
                if (active[i][j]) return false;
            }
        }
        return true;
    }

    public int[] popRandom(Random rnd) {
        if (isEmpty()) {
            return null;
        }

        for (int tries = 0; tries < 10_000 && !pairs.isEmpty(); tries++) {
            int idx = rnd.nextInt(pairs.size());
            int[] pair = pairs.get(idx);
            int i = pair[0];
            int j = pair[1];
            if (i > j) {
                int t = i; i = j; j = t;
            }

            if (active[i][j]) {
                active[i][j] = false;
                pairs.remove(idx);
                return new int[]{i, j};
            } else {
                pairs.remove(idx);
                tries--;
            }
        }

        for (int i = 0; i < maxSize; i++) {
            for (int j = i + 1; j < maxSize; j++) {
                if (active[i][j]) {
                    active[i][j] = false;
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    public void onReplace(int k) {
        for (int i = 0; i < maxSize; i++) {
            if (i == k) {
                continue;
            }
            int a = Math.min(i, k), b = Math.max(i, k);
            if (!active[a][b]) {
                active[a][b] = true;
                pairs.add(new int[]{a, b});
            }
        }
    }

    public void clearAll() {
        pairs.clear();
        for (int i = 0; i < maxSize; i++) {
            for (int j = 0; j < maxSize; j++) {
                active[i][j] = false;
            }
        }
    }
}
