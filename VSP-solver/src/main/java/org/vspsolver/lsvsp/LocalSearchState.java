package org.vspsolver.lsvsp;

import org.graph4j.Graph;
import org.graph4j.vsp.VertexSeparator;
import org.vspsolver.util.Utils;

import java.util.*;

public class LocalSearchState {
    public final Graph graph;
    public final int n;
    public final int maxShoreSize;
    public final Map<Integer, int[]> neighborhood;

    // 0=A, 1=B, 2=C
    public final byte[] part;

    public final int[] nInA;
    public final int[] nInB;

    public final int[] scoreToA;
    public final int[] scoreToB;

    public final int[][] tabuUntil;

    public final BuckerStructure bucketA;
    public final BuckerStructure bucketB;

    public int sizeA;
    public int sizeB;
    public int sizeC;

    private final double tabuMinFrac;
    private final double tabuMaxFrac;
    private final boolean tabuEnabled;

    public LocalSearchState(Graph graph, int maxShoreSize, Map<Integer, int[]> neighborhood, boolean tabuEnabled) {
        this.graph = graph;
        this.n = graph.numVertices();
        this.maxShoreSize = maxShoreSize;
        this.neighborhood = neighborhood;

        this.part = new byte[n];
        this.nInA = new int[n];
        this.nInB = new int[n];
        this.scoreToA = new int[n];
        this.scoreToB = new int[n];
        this.tabuUntil = new int[n][2];

        int degMax = getMaxDegree(neighborhood, n);
        int minScore = 1 - degMax;
        int maxScore = 1;

        this.bucketA = new BuckerStructure(minScore, maxScore, n);
        this.bucketB = new BuckerStructure(minScore, maxScore, n);

        if (tabuEnabled) {
            this.tabuEnabled = true;
            this.tabuMinFrac = BlsVertexSeparatorAlgorithm.TABU_MIN_FRAC;
            this.tabuMaxFrac = BlsVertexSeparatorAlgorithm.TABU_MAX_FRAC;
        } else {
            this.tabuEnabled = false;
            this.tabuMinFrac = 0.0;
            this.tabuMaxFrac = 0.0;
        }
    }

    public void loadFrom(VertexSeparator sep) {
        for (int v = 0; v < n; v++) {
            part[v] = 2;
        }

        for (int v : sep.leftShore().vertices()) {
            part[v] = 0;
        }
        for (int v : sep.rightShore().vertices()) {
            part[v] = 1;
        }
        for (int v : sep.separator().vertices()) {
            part[v] = 2;
        }

        recomputeSizes();
        recomputeNeighborCounts();
        recomputeScoresAndBuckets();
    }

    public VertexSeparator makeStateIntoSeparator() {
        VertexSeparator sep = new VertexSeparator(graph, maxShoreSize);
        for (int v = 0; v < n; v++) {
            if (part[v] == 0) {
                sep.leftShore().add(v);
            } else if (part[v] == 1) {
                sep.rightShore().add(v);
            } else {
                sep.separator().add(v);
            }
        }
        return sep;
    }

    public void recomputeSizes() {
        sizeA = sizeB = sizeC = 0;
        for (int v = 0; v < n; v++) {
            if (part[v] == 0) sizeA++;
            else if (part[v] == 1) sizeB++;
            else sizeC++;
        }
    }

    public void recomputeNeighborCounts() {
        for (int v = 0; v < n; v++) {
            int countA = 0, countB = 0;
            for (int u : neighborhood.get(v)) {
                if (part[u] == 0) {
                    countA++;
                }
                else if (part[u] == 1) {
                    countB++;
                }
            }
            nInA[v] = countA;
            nInB[v] = countB;
        }
    }

    public void recomputeScoresAndBuckets() {
        bucketA.clear();
        bucketB.clear();

        for (int v = 0; v < n; v++) {
            if (part[v] == 2) {
                scoreToA[v] = 1 - nInB[v];
                scoreToB[v] = 1 - nInA[v];
                bucketA.insert(v, scoreToA[v]);
                bucketB.insert(v, scoreToB[v]);
            } else {
                scoreToA[v] = Integer.MIN_VALUE / 4;
                scoreToB[v] = Integer.MIN_VALUE / 4;
            }
        }
    }

    public boolean isLegalMoveFromC(int v, byte toShore) {
        if (part[v] != 2) {
            return false;
        }
        if (toShore == 0 && sizeA >= maxShoreSize) {
            return false;
        }
        if (toShore == 1 && sizeB >= maxShoreSize) {
            return false;
        }

        byte oppositeShore = (toShore == 0) ? (byte) 1 : (byte) 0;

        int pushed = 0;
        for (int u : neighborhood.get(v)) {
            if (part[u] == oppositeShore) pushed++;
        }
        int oppositeSize = (oppositeShore == 0) ? sizeA : sizeB;

        return (oppositeSize - pushed) > 0;
    }

    public int applyMoveFromC(int v, byte toShore, int itercur, Random rnd) {
        if (part[v] != 2) {
            return 0;
        }

        byte oppositeShore = (toShore == 0) ? (byte) 1 : (byte) 0;

        bucketA.remove(v, scoreToA[v]);
        bucketB.remove(v, scoreToB[v]);

        part[v] = toShore;
        sizeC--;
        if (toShore == 0) sizeA++; else sizeB++;

        int[] neighboursV = neighborhood.get(v);
        int pushedCount = 0;
        for (int u : neighboursV) {
            if (part[u] == oppositeShore) {
                pushedCount++;
            }
        }

        int[] pushed = new int[pushedCount];
        int idx = 0;
        for (int u : neighboursV) {
            if (part[u] == oppositeShore) {
                part[u] = 2;
                pushed[idx++] = u;
            }
        }
        if (pushedCount > 0) {
            if (oppositeShore == 0) {
                sizeA -= pushedCount;
            } else {
                sizeB -= pushedCount;
            }

            sizeC += pushedCount;
            if (tabuEnabled) {
                markTabuForPushed(pushed, oppositeShore, itercur, rnd);
            }
        }

        for (int x : neighboursV) {
            if (toShore == 0) {
                nInA[x]++;
            } else {
                nInB[x]++;
            }
        }

        for (int u : pushed) {
            for (int x : neighborhood.get(u)) {
                if (oppositeShore == 0) nInA[x]--; else nInB[x]--;
            }
        }

        recomputeLocalCounts(v);
        for (int u : pushed) recomputeLocalCounts(u);

        Set<Integer> impacted = new HashSet<>();
        for (int x : neighboursV) {
            if (part[x] == 2) impacted.add(x);
        }
        for (int u : pushed) {
            for (int x : neighborhood.get(u)) {
                if (part[x] == 2) impacted.add(x);
            }
        }

        for (int u : pushed) {
            scoreToA[u] = 1 - nInB[u];
            scoreToB[u] = 1 - nInA[u];
            bucketA.insert(u, scoreToA[u]);
            bucketB.insert(u, scoreToB[u]);
        }

        for (int x : impacted) {
            int oldA = scoreToA[x];
            int oldB = scoreToB[x];
            int newA = 1 - nInB[x];
            int newB = 1 - nInA[x];

            if (newA != oldA) {
                scoreToA[x] = newA;
                bucketA.shift(x, oldA, newA);
            }
            if (newB != oldB) {
                scoreToB[x] = newB;
                bucketB.shift(x, oldB, newB);
            }
        }

        return -1 + pushedCount;
    }

    public void forceNonEmptyShores(Random rnd) {
        if (sizeA == 0) {
            int v = Utils.pickRandomInPart(part, (byte) 2, rnd);
            if (v >= 0) {
                part[v] = 0;
                sizeA++;
                sizeC--;
            }
        }
        if (sizeB == 0) {
            int v = Utils.pickRandomInPart(part, (byte) 2, rnd);
            if (v >= 0) {
                part[v] = 1;
                sizeB++;
                sizeC--;
            }
        }
    }

    public boolean isTabu(int v, byte toShore, int iterCurrent) {
        int idx = (toShore == 0) ? 0 : 1;
        return iterCurrent < tabuUntil[v][idx];
    }

    private void markTabuForPushed(int[] pushed, byte originalShore, int itercur, Random rnd) {
        int cSize = Math.max(1, sizeC);
        double frac = tabuMinFrac + rnd.nextDouble() * (tabuMaxFrac - tabuMinFrac);
        int gamma = Math.max(1, (int) Math.round(frac * cSize));
        int shoreIdx = (originalShore == 0) ? 0 : 1;
        for (int u : pushed) {
            tabuUntil[u][shoreIdx] = itercur + gamma;
        }
    }

    private void recomputeLocalCounts(int v) {
        int a = 0, b = 0;
        for (int u : neighborhood.get(v)) {
            if (part[u] == 0) a++;
            else if (part[u] == 1) b++;
        }
        nInA[v] = a;
        nInB[v] = b;
    }

    private static int getMaxDegree(Map<Integer, int[]> neighborhood, int n) {
        int max = 1;
        for (int v = 0; v < n; v++) {
            max = Math.max(max, neighborhood.get(v).length);
        }
        return max;
    }
}
