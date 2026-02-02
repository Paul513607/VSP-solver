package org.vspsolver.prvsp;

import java.util.Random;
import org.vspsolver.util.GraphNeighbourData;

public class IteratedTabuSearch {
    private final GraphNeighbourData neighbourhoodData;
    private final int maxShoreSize;
    private final Random random;
    private final TabuSearch tabuSearch;

    public IteratedTabuSearch(GraphNeighbourData neighbourhoodData, int maxShoreSize, Random random) {
        this.neighbourhoodData = neighbourhoodData;
        this.maxShoreSize = maxShoreSize;
        this.random = random;
        this.tabuSearch = new TabuSearch(neighbourhoodData, maxShoreSize, random);
    }

    public PrVspSolution improve(PrVspSolution start) {
        PrVspSolution local = tabuSearch.improve(start);
        PrVspSolution perturbed = perturb(local);
        return tabuSearch.improve(perturbed);
    }

    private PrVspSolution perturb(PrVspSolution solution) {
        double rho = PrVertexSeparatorAlgorithm.RHO_MIN + random.nextDouble() *
                (PrVertexSeparatorAlgorithm.RHO_MAX - PrVertexSeparatorAlgorithm.RHO_MIN);
        int k = Math.max(1, (int)Math.round(rho * Math.max(1, solution.sizeC)));

        byte[] part = solution.part.clone();
        int sizeA = solution.sizeA, sizeB = solution.sizeB, sizeC = solution.sizeC;

        for (int t = 0; t < k; t++) {
            int v = PrVspSolutionUtil.pickRandomWithPart(part, (byte)2, random);
            if (v < 0) {
                break;
            }

            byte to = random.nextBoolean() ? (byte)0 : (byte)1;
            if ((to == 0 && sizeA >= maxShoreSize) || (to == 1 && sizeB >= maxShoreSize)) {
                to = (byte)(1 - to);
            }

            byte opposite = (to == 0) ? (byte)1 : (byte)0;

            part[v] = to;
            sizeC--;
            if (to == 0) {
                sizeA++;
            } else {
                sizeB++;
            }

            for (int u : neighbourhoodData.neighbourhoodMatrix[v]) {
                if (part[u] == opposite) {
                    part[u] = 2;
                    sizeC++;
                    if (opposite == 0) {
                        sizeA--;
                    } else {
                        sizeB--;
                    }
                }
            }

            if (sizeA == 0) {
                int x = PrVspSolutionUtil.pickRandomWithPart(part, (byte)2, random);
                if (x>=0) {
                    part[x]=0; sizeA++; sizeC--;
                }
            }
            if (sizeB == 0) {
                int x = PrVspSolutionUtil.pickRandomWithPart(part, (byte)2, random);
                if (x>=0) {
                    part[x]=1; sizeB++; sizeC--;
                }
            }
        }

        return new PrVspSolution(part, sizeA, sizeB, sizeC);
    }
}
