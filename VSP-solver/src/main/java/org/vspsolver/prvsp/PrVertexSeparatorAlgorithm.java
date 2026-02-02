package org.vspsolver.prvsp;

import org.graph4j.Graph;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.util.VertexSet;
import org.graph4j.vsp.VertexSeparatorBase;
import org.vspsolver.util.GraphNeighbourData;
import org.vspsolver.util.GraphNeighbourUtil;

import java.util.*;

public class PrVertexSeparatorAlgorithm extends VertexSeparatorBase {
    public long timeLimitMillis = 10_000;
    public long seed = System.nanoTime();

    public static int REF_SET_SIZE = 20;
    public static double TAU_COEFFICIENT = 0.30;
    public static double ALPHA = 1.60; // tabu tenure
    public static double BETA = 2.40;  // iteration cutoff factor
    public static double RHO_MIN = 0.05;
    public static double RHO_MAX = 0.25;
    public static int MAX_REF_SET_NO_UPDATE = 80;

    private final Graph graph;
    private final int maxShoreSize;

    private final Random random;
    private final GraphNeighbourData neighbourhoodData;

    private final TabuSearch tabuSearch;
    private final IteratedTabuSearch iteratedTabuSearch;
    private final PathRelinking pathRelinking;

    public boolean logMain = true;
    private final int logEveryPairs = 25;
    private long bestFoundAtMs = -1;

    public PrVertexSeparatorAlgorithm(Graph graph, int maxShoreSize) {
        super(graph, maxShoreSize);
        this.graph = graph;
        this.maxShoreSize = maxShoreSize;
        this.random = new Random(seed);

        this.neighbourhoodData = GraphNeighbourUtil.build(graph);
        this.tabuSearch = new TabuSearch(neighbourhoodData, maxShoreSize, random);
        this.iteratedTabuSearch = new IteratedTabuSearch(neighbourhoodData, maxShoreSize, random);
        this.pathRelinking = new PathRelinking(neighbourhoodData, maxShoreSize);
    }

    @Override
    public Graph getGraph() {
        return graph;
    }

    @Override
    public VertexSeparator getSeparator() {
        PrVspSolution best = run();
        return toVertexSeparator(best);
    }

    private PrVspSolution run() {
        long start = System.currentTimeMillis();
        long deadline = start + timeLimitMillis;

        PrVspSolution globalBest = null;
        int outer = 0;

        while (System.currentTimeMillis() < deadline) {
            outer++;

            RefSet refSet = initRefSet();
            PairSet pairSet = new PairSet(REF_SET_SIZE);

            PrVspSolution refBest = refSet.best();
            if (globalBest == null || refBest.objective() < globalBest.objective()) {
                globalBest = refBest;
                bestFoundAtMs = System.currentTimeMillis() - start;
            }

            int noUpdate = 0;
            int pairsDone = 0;

            while (!pairSet.isEmpty() && System.currentTimeMillis() < deadline) {
                int[] ij = pairSet.popRandom(random);
                if (ij == null) {
                    break;
                }

                int i = ij[0];
                int j = ij[1];
                PrVspSolution solutionI = refSet.get(i);
                PrVspSolution solutionJ = refSet.get(j);

                PrVspSolution pathBest1 = pathRelinking.bestOnPath(solutionI, solutionJ);
                PrVspSolution pathBest2 = pathRelinking.bestOnPath(solutionJ, solutionI);

                PrVspSolution improved1 = iteratedTabuSearch.improve(pathBest1);
                PrVspSolution improved2 = iteratedTabuSearch.improve(pathBest2);

                PrVspSolution newSolution = (improved1.objective() <= improved2.objective()) ? improved1 : improved2;

                if (globalBest == null || newSolution.objective() < globalBest.objective()) {
                    globalBest = newSolution;
                    bestFoundAtMs = System.currentTimeMillis() - start;
                    logImprove(outer, pairsDone + 1, "ITS", globalBest.objective(), start);
                }

                int tau = (int)Math.round(TAU_COEFFICIENT * Math.max(1, newSolution.sizeC));
                int replaced = refSet.update(newSolution, tau);

                if (replaced >= 0) {
                    pairSet.onReplace(replaced);
                    noUpdate = 0;
                } else {
                    noUpdate++;
                }

                pairsDone++;

                logPairIteration(outer, pairsDone, globalBest, refSet,
                        pathBest1, pathBest2, improved1, improved2, newSolution,
                        tau, replaced, noUpdate, start, deadline);

                if (noUpdate >= MAX_REF_SET_NO_UPDATE) {
                    pairSet.clearAll();
                    break;
                }
            }

            logOuterEnd(outer, pairsDone, globalBest, refSet, start);
        }

        return globalBest;
    }

    private RefSet initRefSet() {
        List<PrVspSolution> pool = new ArrayList<>(2 * REF_SET_SIZE);
        for (int k = 0; k < 2 * REF_SET_SIZE; k++) {
            PrVspSolution solution0 = PrVspSolutionUtil.randomInitial(neighbourhoodData, maxShoreSize, random);
            PrVspSolution solution1 = tabuSearch.improve(solution0);
            pool.add(solution1);
        }
        return RefSet.selectBestNonIdentical(pool, REF_SET_SIZE);
    }

    private VertexSeparator toVertexSeparator(PrVspSolution s) {
        VertexSeparator vertexSeparator = new VertexSeparator(graph, maxShoreSize);

        VertexSet A = vertexSeparator.leftShore();
        VertexSet B = vertexSeparator.rightShore();
        VertexSet C = vertexSeparator.separator();

        for (int idx = 0; idx < neighbourhoodData.size(); idx++) {
            int id = neighbourhoodData.ids[idx];
            if (s.part[idx] == 0) {
                A.add(id);
            }
            else if (s.part[idx] == 1) {
                B.add(id);
            }
            else {
                C.add(id);
            }
        }
        return vertexSeparator;
    }

    private void logPairIteration(int outer, int pairIter,
                                  PrVspSolution globalBest, RefSet refSet,
                                  PrVspSolution pathBest1, PrVspSolution pathBest2,
                                  PrVspSolution improved1, PrVspSolution improved2,
                                  PrVspSolution newSolution,
                                  int tau, int replaced, int noUpdate,
                                  long startMs, long deadlineMs) {
        if (!logMain) return;
        if (pairIter % logEveryPairs != 0) return;

        long now = System.currentTimeMillis();
        long elapsed = now - startMs;
        long left = deadlineMs - now;

        System.out.printf(
                "PR outer=%d pair=%d best=%d refBest=%d newSolution=%d " +
                        "path=(%d,%d) its=(%d,%d) tau=%d rep=%d noUpd=%d t=%dms left=%dms%n",
                outer, pairIter,
                globalBest.objective(),
                refSet.best().objective(),
                newSolution.objective(),
                pathBest1.objective(), pathBest2.objective(),
                improved1.objective(), improved2.objective(),
                tau, replaced, noUpdate,
                elapsed, left
        );
    }

    private void logOuterEnd(int outer, int pairsDone,
                             PrVspSolution globalBest, RefSet refSet,
                             long startMs) {
        if (!logMain) return;
        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("PR OUTER_END outer=%d pairs=%d best=%d refBest=%d t=%dms%n",
                outer, pairsDone,
                globalBest.objective(),
                refSet.best().objective(),
                elapsed
        );
    }

    private void logImprove(int outer, int pairIter, String source,
                            int newBest, long startMs) {
        if (!logMain) return;
        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("PR IMPROVE outer=%d pair=%d by=%s best=%d t=%dms%n",
                outer, pairIter, source, newBest, elapsed
        );
    }

    public long getBestFoundAtMs() {
        return bestFoundAtMs;
    }
}
