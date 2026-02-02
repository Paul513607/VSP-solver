package org.vspsolver.prvsp;

import org.vspsolver.util.GraphNeighbourData;

import java.util.*;

public final class ExteriorPathRelinking {

    private final GraphNeighbourData neighbourhoodData;
    private final int maxShoreSize;
    private final Random random;

    private final int maxSteps;
    private final int candListSize;

    public ExteriorPathRelinking(GraphNeighbourData neighbourhoodData,
                                 int maxShoreSize,
                                 Random random,
                                 int maxSteps,
                                 int candListSize) {
        this.neighbourhoodData = neighbourhoodData;
        this.maxShoreSize = maxShoreSize;
        this.random = random;
        this.maxSteps = Math.max(1, maxSteps);
        this.candListSize = Math.max(1, candListSize);
    }

    public PrVspSolution bestOnExteriorPath(PrVspSolution start,
                                            PrVspSolution initiatingSolution,
                                            PrVspSolution guidingSolution) {

        List<Integer> J = buildJ(initiatingSolution, guidingSolution);
        if (J.isEmpty()) {
            return start;
        }

        PrVspSolution current = start;
        PrVspSolution best = start;

        int steps = Math.min(maxSteps, J.size());

        for (int step = 0; step < steps; step++) {
            Pick pick = chooseFlipFromCandidateList(current, J);
            if (pick.v < 0) {
                break;
            }

            current = pick.next;
            if (current.objective() < best.objective()) {
                best = current;
            }

            int last = J.size() - 1;
            Collections.swap(J, pick.index, last);
            J.remove(last);
        }

        return best;
    }

    private List<Integer> buildJ(PrVspSolution initiatingSolution, PrVspSolution guidingSOlution) {
        int n = initiatingSolution.part.length;
        List<Integer> J = new ArrayList<>(n);
        for (int v = 0; v < n; v++) {
            boolean bi = initiatingSolution.sepBits.get(v);
            boolean bg = guidingSOlution.sepBits.get(v);
            if (bi == bg) {
                J.add(v);
            }
        }
        return J;
    }

    private Pick chooseFlipFromCandidateList(PrVspSolution current, List<Integer> J) {
        int m = J.size();
        if (m == 0) {
            return new Pick(-1, -1, null);
        }

        int sampleSize = Math.min(candListSize, m);

        int bestV = -1;
        int bestIdx = -1;
        PrVspSolution bestNext = null;
        int bestObj = Integer.MAX_VALUE;

        int[] feasibleIdx = new int[sampleSize];
        PrVspSolution[] feasibleNext = new PrVspSolution[sampleSize];
        int feasibleCount = 0;

        for (int t = 0; t < sampleSize; t++) {
            int idx = random.nextInt(m);
            int v = J.get(idx);

            PrVspSolution cand = PathRelinking.tryFlip(current, v, neighbourhoodData, maxShoreSize, random);
            if (cand == null) {
                continue;
            }

            feasibleIdx[feasibleCount] = idx;
            feasibleNext[feasibleCount] = cand;
            feasibleCount++;

            int obj = cand.objective();
            if (obj < bestObj) {
                bestObj = obj;
                bestV = v;
                bestIdx = idx;
                bestNext = cand;
            }
        }

        if (feasibleCount == 0) {
            return new Pick(-1, -1, null);
        }

        if (random.nextDouble() < 0.9) {
            int k = random.nextInt(feasibleCount);
            int idx = feasibleIdx[k];
            int v = J.get(idx);
            return new Pick(v, idx, feasibleNext[k]);
        }

        return new Pick(bestV, bestIdx, bestNext);
    }

    public static final class Pick {
        public final int v;
        public final int index;
        public final PrVspSolution next;

        public Pick(int v, int index, PrVspSolution next) {
            this.v = v;
            this.index = index;
            this.next = next;
        }
    }
}
