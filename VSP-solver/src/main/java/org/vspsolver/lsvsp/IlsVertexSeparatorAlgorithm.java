package org.vspsolver.lsvsp;

import org.graph4j.Graph;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.vsp.VertexSeparatorBase;
import org.vspsolver.util.GraphNeighbourData;
import org.vspsolver.util.GraphNeighbourUtil;
import org.vspsolver.util.Utils;

import java.util.Map;
import java.util.Random;

public class IlsVertexSeparatorAlgorithm extends VertexSeparatorBase {
    public long timeLimitMillis = 10_000;
    public long seed = System.nanoTime();

    public static int K_MIN = 1;
    public static int K_MAX = 15;

    public static int THETA = 1;
    public static int NO_IMPROVE_LIMIT = 200;

    private final Graph graph;
    private final int n;
    private final int maxShoreSize;

    private final Random random;

    private final Map<Integer, int[]> neighbourhood;
    private final LocalSearchState state;

    private VertexSeparator best;
    private int iterCurrent = 0;

    private final boolean logIterations = true;
    private final int logEvery = 100;
    private long bestFoundAtMs = -1;
    private int bestObjective = Integer.MAX_VALUE;

    public IlsVertexSeparatorAlgorithm(Graph graph, int maxShoreSize) {
        super(graph, maxShoreSize);
        this.graph = graph;
        this.n = graph.numVertices();
        this.maxShoreSize = maxShoreSize;

        this.random = new Random(seed);

        GraphNeighbourData data = GraphNeighbourUtil.build(graph);
        this.neighbourhood = data.getNeighbourhoodAsMap();
        this.state = new LocalSearchState(graph, maxShoreSize, neighbourhood, false);

        this.best = new VertexSeparator(graph, maxShoreSize);
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

        int currentCost = current.separator().size();
        int bestCost = currentCost;

        int noImprove = 0;

        if (logIterations) {
            logIteration(iterCurrent, current.separator().size(), best.separator().size(),
                    System.currentTimeMillis() - startTime);
        }

        while (System.currentTimeMillis() < deadline) {
            iterCurrent++;

            int k = K_MIN + random.nextInt(K_MAX - K_MIN + 1);
            VertexSeparator candidate = perturbByFixedKTimes(current, k);

            candidate = descentBasedSearch(candidate);
            int candidateCost = candidate.separator().size();

            if (accept(candidateCost, currentCost)) {
                current = candidate;
                currentCost = candidateCost;
            }

            if (candidateCost < bestCost) {
                best = new VertexSeparator(candidate);
                bestCost = candidateCost;
                noImprove = 0;

                bestObjective = best.separator().size();
                bestFoundAtMs = System.currentTimeMillis() - startTime;
            } else {
                noImprove++;
            }

            if (logIterations && (iterCurrent % logEvery == 0)) {
                logIteration(iterCurrent, current.separator().size(), best.separator().size(),
                        System.currentTimeMillis() - startTime);
            }
        }
    }

    private boolean accept(int candidateCost, int currentCost) {
        return candidateCost <= currentCost + THETA;
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
            int v = Utils.pickAny(sep.separator(), random);
            sep.separator().remove(v);
            sep.leftShore().add(v);
        }
        if (sep.rightShore().isEmpty()) {
            int v = Utils.pickAny(sep.separator(), random);
            sep.separator().remove(v);
            sep.rightShore().add(v);
        }
    }

    private VertexSeparator descentBasedSearch(VertexSeparator start) {
        state.loadFrom(start);

        while (true) {
            int vA = state.bucketA.peekMaxVertex();
            int sA = (vA >= 0) ? state.scoreToA[vA] : Integer.MIN_VALUE;

            int vB = state.bucketB.peekMaxVertex();
            int sB = (vB >= 0) ? state.scoreToB[vB] : Integer.MIN_VALUE;

            int bestV = -1;
            byte bestTo = -1;
            int bestScore = Integer.MIN_VALUE;

            if (vA >= 0 && state.isLegalMoveFromC(vA, (byte)0) && sA > bestScore) {
                bestV = vA; bestTo = 0; bestScore = sA;
            }
            if (vB >= 0 && state.isLegalMoveFromC(vB, (byte)1) && sB > bestScore) {
                bestV = vB; bestTo = 1; bestScore = sB;
            }

            if (bestV < 0) {
                break;
            }
            if (bestScore <= 0) {
                break;
            }

            state.applyMoveFromC(bestV, bestTo, 0, random);
            state.forceNonEmptyShores(random);
        }

        return state.makeStateIntoSeparator();
    }

    private VertexSeparator perturbByFixedKTimes(VertexSeparator current, int k) {
        state.loadFrom(current);

        for (int i = 0; i < k; i++) {
            int v = Utils.pickRandomInPart(state.part, (byte)2, random);
            if (v < 0) {
                break;
            }

            byte to = random.nextBoolean() ? (byte)0 : (byte)1;
            if (!state.isLegalMoveFromC(v, to)) {
                to = (to == 0) ? (byte)1 : (byte)0;
                if (!state.isLegalMoveFromC(v, to)) {
                    continue;
                }
            }
            state.applyMoveFromC(v, to, 0, random);
            state.forceNonEmptyShores(random);
        }

        return state.makeStateIntoSeparator();
    }

    private void logIteration(int iter,int currC,int bestC,long elapsedMs
    ) {
        System.out.printf("iter=%d |C|=%d best=%d time=%dms%n",
                iter,
                currC,
                bestC,
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
