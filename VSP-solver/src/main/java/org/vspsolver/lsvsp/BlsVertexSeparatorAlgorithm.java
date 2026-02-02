package org.vspsolver.lsvsp;

import org.graph4j.Graph;
import org.graph4j.util.VertexSet;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.vsp.VertexSeparatorBase;
import org.vspsolver.util.GraphNeighbourData;
import org.vspsolver.util.GraphNeighbourUtil;
import org.vspsolver.util.Utils;

import java.util.BitSet;
import java.util.Map;
import java.util.Random;

public class BlsVertexSeparatorAlgorithm extends VertexSeparatorBase {

    public long timeLimitMillis = 10_000;
    public long seed = System.nanoTime();

    public static double TABU_MIN_FRAC = 0.2;
    public static double TABU_MAX_FRAC = 0.7;

    public static double ALPHA_NC = 0.6;
    public static double ALPHA_C  = 0.2;
    public static double BETA     = 4.0;

    public double LMIN_FRAC = 0.05;
    public double LMAX_FRAC = 0.25;

    public int MAXHS  = 100000;
    public int MAXSLO = 500;

    private final Graph graph;
    private final int n;
    private final int maxShoreSize;

    private final Random random;
    private final LocalSearchState state;
    private final HashMemory hashMemory;

    private int iterCurrent;
    private int lastCycle;
    private double wc;
    private int maxInc = 100;

    private Map<Integer, int[]> neighbourhood;

    private VertexSeparator best;

    private final boolean logIterations = true;
    private final int logEvery = 100;
    private long bestFoundAtMs = -1;
    private int bestObjective = Integer.MAX_VALUE;

    public BlsVertexSeparatorAlgorithm(Graph graph, int maxShoreSize) {
        super(graph, maxShoreSize);
        this.graph = graph;
        this.n = graph.numVertices();
        this.maxShoreSize = maxShoreSize;

        GraphNeighbourData data = GraphNeighbourUtil.build(graph);
        this.neighbourhood = data.getNeighbourhoodAsMap();

        this.random = new Random(seed);
        this.state = new LocalSearchState(graph, maxShoreSize, neighbourhood, true);
        this.hashMemory = new HashMemory(n, MAXHS, MAXSLO, random);

        this.iterCurrent = 0;
        this.lastCycle = 0;
        this.wc = 0;

        this.best = new VertexSeparator(graph, maxShoreSize);
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    @Override
    public VertexSeparator getSeparator() {
        run();
        return new VertexSeparator(best);
    }

    private void run() {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + timeLimitMillis;

        VertexSeparator current = generateInitialSolution();
        current = descentBasedSearch(current);
        best = new VertexSeparator(current);
        bestObjective = best.separator().size();
        bestFoundAtMs = System.currentTimeMillis() - startTime;

        int L = clampJumpMagnitude((int)Math.round(LMIN_FRAC * Math.max(1, current.separator().size())),
                current.separator().size());

        if (logIterations) {
            logIteration(iterCurrent, current.separator().size(), best.separator().size(), L, null, -1,
                    System.currentTimeMillis() - startTime);
        }

        while (System.currentTimeMillis() < deadline) {
            iterCurrent++;

            int prev = previousEncounter(current);

            L = determineJumpMagnitude(L, prev, current.separator().size());
            PerturbationType perturbationType = determinePerturbationType(prev);

            current = perturb(current, L, perturbationType);
            current = descentBasedSearch(current);

            if (current.separator().size() < best.separator().size()) {
                best = new VertexSeparator(current);

                bestObjective = best.separator().size();
                bestFoundAtMs = System.currentTimeMillis() - startTime;
            }

            if (logIterations && (iterCurrent % logEvery == 0)) {
                logIteration(iterCurrent, current.separator().size(), best.separator().size(), L, perturbationType, prev,
                        System.currentTimeMillis() - startTime);
            }
        }
    }

    private VertexSeparator generateInitialSolution() {
        VertexSeparator sep = new VertexSeparator(graph, maxShoreSize);

        int[] vertices = graph.vertices();
        Utils.shuffle(vertices, random);

        sep.leftShore().add(vertices[0]);
        sep.rightShore().add(vertices[1]);

        for (int i = 2; i < vertices.length; i++) {
            int v = vertices[i];
            if (sep.leftShore().size() < maxShoreSize && sep.rightShore().size() < maxShoreSize) {
                if (random.nextBoolean()) sep.leftShore().add(v);
                else sep.rightShore().add(v);
            } else if (sep.leftShore().size() < maxShoreSize) {
                sep.leftShore().add(v);
            } else if (sep.rightShore().size() < maxShoreSize) {
                sep.rightShore().add(v);
            } else {
                sep.separator().add(v);
            }
        }

        repairABEdgesByPushingToC(sep);

        ensureNonEmptyShores(sep);
        return sep;
    }

    private void repairABEdgesByPushingToC(VertexSeparator sep) {
        boolean changed;
        do {
            changed = false;
            int[] A = sep.leftShore().vertices();
            for (int a : A) {
                for (int u : neighbourhood.get(a)) {
                    if (sep.rightShore().contains(u)) {
                        if (random.nextBoolean()) {
                            sep.leftShore().remove(a);
                            sep.separator().add(a);
                        } else {
                            sep.rightShore().remove(u);
                            sep.separator().add(u);
                        }
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    break;
                }
            }
        } while (changed);
    }

    private void ensureNonEmptyShores(VertexSeparator sep) {
        if (sep.leftShore().isEmpty()) {
            int vertex = Utils.pickAny(sep.separator(), random);
            sep.separator().remove(vertex);
            sep.leftShore().add(vertex);
        }
        if (sep.rightShore().isEmpty()) {
            int vertex = Utils.pickAny(sep.separator(), random);
            sep.separator().remove(vertex);
            sep.rightShore().add(vertex);
        }
    }

    private VertexSeparator descentBasedSearch(VertexSeparator start) {
        state.loadFrom(start);

        while (true) {
            int vA = state.bucketA.peekMaxVertex();
            int sA = (vA >= 0) ? state.scoreToA[vA] : Integer.MIN_VALUE;

            int vB = state.bucketB.peekMaxVertex();
            int sB = (vB >= 0) ? state.scoreToB[vB] : Integer.MIN_VALUE;

            Move best = null;

            if (vA >= 0 && state.isLegalMoveFromC(vA, (byte)0)) {
                best = new Move(vA, (byte)0, sA);
            }
            if (vB >= 0 && state.isLegalMoveFromC(vB, (byte)1)) {
                if (best == null || sB > best.score) best = new Move(vB, (byte)1, sB);
            }

            if (best == null) {
                best = scanBestLegalMove();
            }
            if (best == null || best.score <= 0) {
                break;
            }

            state.applyMoveFromC(best.v, best.toShore, iterCurrent, random);
            state.forceNonEmptyShores(random);
        }

        return state.makeStateIntoSeparator();
    }

    private Move scanBestLegalMove() {
        int bestV = -1;
        byte bestTo = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int v = 0; v < n; v++) {
            if (state.part[v] != 2) {
                continue;
            }
            if (state.isLegalMoveFromC(v, (byte)0)) {
                int sc = state.scoreToA[v];
                if (sc > bestScore) {
                    bestScore = sc;
                    bestV = v;
                    bestTo = 0;
                }
            }
            if (state.isLegalMoveFromC(v, (byte)1)) {
                int sc = state.scoreToB[v];
                if (sc > bestScore) {
                    bestScore = sc;
                    bestV = v;
                    bestTo = 1;
                }
            }
        }

        if (bestV < 0) {
            return null;
        }
        return new Move(bestV, bestTo, bestScore);
    }

    private int determineJumpMagnitude(int L, int prevVisit, int cSize) {
        if (prevVisit != -1) {
            lastCycle = iterCurrent;
            L = L + 1;
            wc = 0.9 * wc + 0.1 * Math.max(1, iterCurrent - prevVisit);
        } else {
            if ((iterCurrent - lastCycle) > wc * BETA) {
                L = L - 1;
            }
        }
        return clampJumpMagnitude(L, cSize);
    }

    private int clampJumpMagnitude(int L, int cSize) {
        int C = Math.max(1, cSize);
        int LMIN = Math.max(1, (int)Math.round(LMIN_FRAC * C));
        int LMAX = Math.max(LMIN, (int)Math.round(LMAX_FRAC * C));
        if (L < LMIN) {
            L = LMIN;
        }
        if (L > LMAX) {
            L = LMAX;
        }
        return L;
    }

    private PerturbationType determinePerturbationType(int prevVisit) {
        int maxSloCount = Math.max(1, MAXSLO);

        double e;
        if (prevVisit == -1) {
            e = ALPHA_NC + (double)(iterCurrent - lastCycle) / Math.max(1, maxInc);
        } else {
            e = 1.0 - ALPHA_C - (double)(iterCurrent - prevVisit) / maxSloCount;
        }
        if (e < 0) {
            e = 0;
        } else if (e > 1) {
            e = 1;
        }

        double randomNum = random.nextInt(101) / 100.0;
        return (e >= randomNum) ? PerturbationType.DIRP : PerturbationType.RNDP;
    }

    private VertexSeparator perturb(VertexSeparator current, int L, PerturbationType type) {
        state.loadFrom(current);

        for (int i = 0; i < L; i++) {
            if (type == PerturbationType.RNDP) {
                randomPerturbMove();
            } else {
                directedPerturbMove();
            }
            state.forceNonEmptyShores(random);
        }

        return state.makeStateIntoSeparator();
    }

    private void randomPerturbMove() {
        int v = Utils.pickRandomInPart(state.part, (byte)2, random);
        if (v < 0) {
            return;
        }

        byte to = random.nextBoolean() ? (byte)0 : (byte)1;
        if (!state.isLegalMoveFromC(v, to)) {
            if (to == 0) {
                to = (byte)1;
            } else {
                to = (byte)0;
            }

            if (!state.isLegalMoveFromC(v, to)) {
                return;
            }
        }
        state.applyMoveFromC(v, to, iterCurrent, random);
    }

    private void directedPerturbMove() {
        int bestV = -1;
        byte bestTo = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int v = 0; v < n; v++) {
            if (state.part[v] != 2) continue;

            if (state.isLegalMoveFromC(v, (byte)0) && !state.isTabu(v, (byte)0, iterCurrent)) {
                int scoreC = state.scoreToA[v];
                if (scoreC > bestScore) { bestScore = scoreC; bestV = v; bestTo = 0; }
            }
            if (state.isLegalMoveFromC(v, (byte)1) && !state.isTabu(v, (byte)1, iterCurrent)) {
                int scoreC = state.scoreToB[v];
                if (scoreC > bestScore) {
                    bestScore = scoreC;
                    bestV = v;
                    bestTo = 1;
                }
            }
        }

        if (bestV < 0) {
            Move move = scanBestLegalMove();
            if (move == null) {
                return;
            }
            bestV = move.v;
            bestTo = move.toShore;
        }

        state.applyMoveFromC(bestV, bestTo, iterCurrent, random);
    }

    private int previousEncounter(VertexSeparator current) {
        BitSet bitSet = separatorBitSet(current.separator());
        return hashMemory.touch(bitSet, iterCurrent);
    }

    private BitSet separatorBitSet(VertexSet sepSet) {
        BitSet bs = new BitSet(n);
        for (int v : sepSet.vertices()) bs.set(v);
        return bs;
    }

    private enum PerturbationType { DIRP, RNDP }

    private static final class Move {
        final int v;
        final byte toShore;
        final int score;
        Move(int v, byte toShore, int score) {
            this.v = v;
            this.toShore = toShore;
            this.score = score;
        }
    }

    private void logIteration(int iter, int currC, int bestC, int L, PerturbationType type, int prevVisit, long elapsedMs
    ) {
        System.out.printf("iter=%d |C|=%d best=%d L=%d pert=%s cycle=%s time=%dms%n",
                iter,
                currC,
                bestC,
                L,
                type,
                (prevVisit != -1 ? "YES" : "NO"),
                elapsedMs
        );
    }

    public int getBestObjective() {
        return bestObjective;
    }

    public long getBestFoundAtMs() {
        return bestFoundAtMs;
    }
}
