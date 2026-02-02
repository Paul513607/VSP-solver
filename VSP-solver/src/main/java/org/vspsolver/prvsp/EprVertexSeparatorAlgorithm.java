package org.vspsolver.prvsp;

import org.graph4j.Graph;
import org.graph4j.util.VertexSet;
import org.graph4j.vsp.VertexSeparator;
import org.graph4j.vsp.VertexSeparatorBase;
import org.vspsolver.util.GraphNeighbourData;
import org.vspsolver.util.GraphNeighbourUtil;

import java.util.*;

public class EprVertexSeparatorAlgorithm extends VertexSeparatorBase {
    public long timeLimitMillis = 10_000;
    public long seed = 123;

    public static int REF_SET_SIZE = 20;
    public static double TAU_COEFFICIENT = 0.30;
    public static double ALPHA = 1.60;
    public static double BETA = 2.40;
    public static double RHO_MIN = 0.05;
    public static double RHO_MAX = 0.25;
    public static int MAX_NO_REF_SET_UPDATE = 80;

    public static boolean ENABLE_EPR = true;
    public static double EPR_RATE = 0.50;
    public static int EPR_STAGNATION_PAIRS = 5;
    public static int EXTERIOR_MAX_STEPS = 50;
    public static int EXTERIOR_CANDIDATE_LIST_SIZE = 50;
    public static boolean EXTERIOR_STOP_IF_NO_IMPROVEMENT = false;
    public static int EXTERIOR_NO_IMPROVEMENT_LIMIT = 10;

    private final Graph graph;
    private final int maxShoreSize;

    private final Random random;
    private final GraphNeighbourData neighbourhoodData;

    private final TabuSearch tabuSearch;
    private final IteratedTabuSearch iteratedTabuSearch;
    private final PathRelinking pathRelinking;
    private final ExteriorPathRelinking exteriorPathRelinking;

    public boolean logMain = true;
    private final int logEveryPairs = 25;
    private long bestFoundAtMs = -1;

    public EprVertexSeparatorAlgorithm(Graph graph, int maxShoreSize) {
        super(graph, maxShoreSize);
        this.graph = graph;
        this.maxShoreSize = maxShoreSize;
        this.random = new Random(seed);

        this.neighbourhoodData = GraphNeighbourUtil.build(graph);

        this.tabuSearch = new TabuSearch(neighbourhoodData, maxShoreSize, random);
        this.iteratedTabuSearch = new IteratedTabuSearch(neighbourhoodData, maxShoreSize, random);
        this.pathRelinking = new PathRelinking(neighbourhoodData, maxShoreSize);
        this.exteriorPathRelinking = new ExteriorPathRelinking(
                neighbourhoodData,
                maxShoreSize,
                random,
                EXTERIOR_MAX_STEPS,
                EXTERIOR_CANDIDATE_LIST_SIZE
        );
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
            boolean usedEpr;
            PrVspSolution extPathBest;
            PrVspSolution extImproved;

            outer++;

            RefSet refSet = initRefSet();
            int p = refSet.size();
            PairSet pairSet = new PairSet(p);
            if (p < 2) {
                continue;
            }

            PrVspSolution refBest = refSet.best();
            if (globalBest == null || refBest.objective() < globalBest.objective()) {
                globalBest = refBest;
                bestFoundAtMs = System.currentTimeMillis() - start;
            }

            int noUpdate = 0;
            int pairsDone = 0;
            int noBestImprovePairs = 0;

            /*System.out.println("ENTER INNER: outer=" + outer +
                    " refSize=" + refSet.size() +
                    " pairSetEmpty=" + pairSet.isEmpty());*/
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
                    noBestImprovePairs = 0;
                    bestFoundAtMs = System.currentTimeMillis() - start;
                    logImprove(outer, pairsDone + 1, "ITS", globalBest.objective(), start); // LOG:
                } else {
                    noBestImprovePairs++;
                    // System.out.println("Global best:" + globalBest.objective());
                }

                usedEpr = false;
                extPathBest = null;
                extImproved = null;

                boolean doEpr = ENABLE_EPR &&
                        (random.nextDouble() < EPR_RATE || noBestImprovePairs >= EPR_STAGNATION_PAIRS);

                if (doEpr) {
                    PrVspSolution extBestOnPath = exteriorPathRelinking.bestOnExteriorPath(newSolution, solutionI, solutionJ);
                    extImproved = iteratedTabuSearch.improve(extBestOnPath);

                    if (extBestOnPath.objective() < globalBest.objective()) {
                        usedEpr = true;
                        globalBest = extBestOnPath;
                        bestFoundAtMs = System.currentTimeMillis() - start;
                        logImprove(outer, pairsDone + 1, "EPR", globalBest.objective(), start);
                        noBestImprovePairs = 0;
                    }

                    if (extImproved.objective() < globalBest.objective()) {
                        usedEpr = true;
                        globalBest = extImproved;
                        bestFoundAtMs = System.currentTimeMillis() - start;
                        logImprove(outer, pairsDone + 1, "EPR+ITS", globalBest.objective(), start);
                        noBestImprovePairs = 0;
                    }

                    noUpdate = tryInsert(refSet, pairSet, extImproved, noUpdate);
                    noUpdate = tryInsert(refSet, pairSet, newSolution, noUpdate);

                    /*if (logMain) {
                        System.out.printf("EPR CALLED outer=%d pair=%d stagn=%d sn=%d ext=%d extITS=%d%n",
                                outer, pairsDone + 1,
                                noBestImprovePairs,
                                newSolution.objective(),
                                extBestOnPath.objective(),
                                extImproved.objective());
                    }*/
                } else {
                    noUpdate = tryInsert(refSet, pairSet, newSolution, noUpdate);
                }

                pairsDone++;

                int tauLog = (int)Math.round(TAU_COEFFICIENT * Math.max(1, newSolution.sizeC));
                int replacedLog = -2;
                logPairIteration(outer, pairsDone, globalBest, refSet, newSolution, // LOG:
                        usedEpr, extPathBest, extImproved,
                        tauLog, replacedLog, noUpdate,
                        start, deadline);

                if (noUpdate >= MAX_NO_REF_SET_UPDATE) {
                    pairSet.clearAll();
                    break;
                }
            }

            logOuterEnd(outer, pairsDone, globalBest, refSet, start);
        }

        return globalBest;
    }

    private int tryInsert(RefSet refSet, PairSet pairSet, PrVspSolution cand, int noUpdate) {
        int tau = (int) Math.round(TAU_COEFFICIENT * Math.max(1, cand.sizeC));
        int replaced = refSet.update(cand, tau);

        if (replaced >= 0) {
            pairSet.onReplace(replaced);
            return 0;
        }
        return noUpdate + 1;
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

        for (int idx = 0; idx < neighbourhoodData.ids.length; idx++) {
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
                                  PrVspSolution newSolution,
                                  boolean usedEpr,
                                  PrVspSolution extPathBest, PrVspSolution extImproved,
                                  int tau, int replaced, int noUpdate,
                                  long startMs, long deadlineMs) {
        if (!logMain) return;
        if (pairIter % logEveryPairs != 0) return;

        long now = System.currentTimeMillis();
        long elapsed = now - startMs;
        long left = deadlineMs - now;

        int extPathObj = (extPathBest == null) ? -1 : extPathBest.objective();
        int extItsObj  = (extImproved == null) ? -1 : extImproved.objective();

        System.out.printf(
                "EPR outer=%d pair=%d best=%d refBest=%d newSolution=%d epr=%s extPath=%d extITS=%d " +
                        "tau=%d rep=%d noUpd=%d t=%dms left=%dms%n",
                outer, pairIter,
                globalBest.objective(),
                refSet.best().objective(),
                newSolution.objective(),
                usedEpr ? "YES" : "NO",
                extPathObj, extItsObj,
                tau, replaced, noUpdate,
                elapsed, left
        );
    }

    private void logOuterEnd(int outer, int pairsDone,
                             PrVspSolution globalBest, RefSet refSet,
                             long startMs) {
        if (!logMain) return;
        long elapsed = System.currentTimeMillis() - startMs;
        System.out.printf("EPR OUTER_END outer=%d pairs=%d best=%d refBest=%d t=%dms%n",
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
        System.out.printf("EPR IMPROVE outer=%d pair=%d by=%s best=%d t=%dms%n",
                outer, pairIter, source, newBest, elapsed
        );
    }

    public long getBestFoundAtMs() {
        return bestFoundAtMs;
    }
}
