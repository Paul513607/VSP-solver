package org.vspsolver.prvsp;

import java.util.*;

public class RefSet {
    private final List<PrVspSolution> solutions;

    public RefSet(List<PrVspSolution> solutions) {
        this.solutions = solutions;
    }

    public int size() {
        return solutions.size();
    }

    public PrVspSolution get(int i) {
        return solutions.get(i);
    }

    public PrVspSolution best() {
        PrVspSolution best = null;
        for (PrVspSolution solution : solutions) {
            if (best == null || solution.objective() < best.objective()) {
                best = solution;
            }
        }
        return best;
    }

    public int worstIndex() {
        int worstIdx = 0;
        for (int i = 1; i < solutions.size(); i++) {
            if (solutions.get(i).objective() > solutions.get(worstIdx).objective()) {
                worstIdx = i;
            }
        }
        return worstIdx;
    }

    public int closestIndex(PrVspSolution newSolution) {
        int bestI = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < solutions.size(); i++) {
            int d = PrVspSolution.sepDistance(solutions.get(i), newSolution);
            if (d < bestD) {
                bestD = d;
                bestI = i;
            }
        }
        return bestI;
    }

    public int update(PrVspSolution newSolution, int tau) {
        int c = closestIndex(newSolution);
        int dmin = PrVspSolution.sepDistance(solutions.get(c), newSolution);

        if (dmin <= tau) {
            if (newSolution.objective() <= solutions.get(c).objective()) {
                solutions.set(c, newSolution);
                return c;
            }
            return -1;
        } else {
            int worst = worstIndex();
            if (newSolution.objective() <= solutions.get(worst).objective()) {
                solutions.set(worst, newSolution);
                return worst;
            }
            return -1;
        }
    }

    public static RefSet selectBestNonIdentical(List<PrVspSolution> pool, int maxSize) {
        pool.sort(Comparator.comparingInt(PrVspSolution::objective));
        List<PrVspSolution> outSolution = new ArrayList<>(maxSize);
        Set<String> seen = new HashSet<>();
        for (PrVspSolution s : pool) {
            String sig = s.sepBits.toString();
            if (seen.add(sig)) {
                outSolution.add(s);
                if (outSolution.size() == maxSize) break;
            }
        }
        return new RefSet(outSolution);
    }
}
