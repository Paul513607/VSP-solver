package org.vspsolver.prvsp;

import org.vspsolver.util.GraphNeighbourData;

import java.util.*;

public class PathRelinking {
    private final GraphNeighbourData neighbourhoodData;
    private final int maxShoreSize;

    public PathRelinking(GraphNeighbourData neighbourhoodData, int maxShoreSize) {
        this.neighbourhoodData = neighbourhoodData;
        this.maxShoreSize = maxShoreSize;
    }

    public PrVspSolution bestOnPath(PrVspSolution initialSolution, PrVspSolution guidingSolution) {
        BitSet symmetricDifference = (BitSet) initialSolution.sepBits.clone();
        symmetricDifference.xor(guidingSolution.sepBits);
        int symmetricDifferenceCard = symmetricDifference.cardinality();
        if (symmetricDifferenceCard <= 1) {
            return initialSolution;
        }

        PrVspSolution current = initialSolution;
        PrVspSolution best = initialSolution;

        for (int step = 1; step < symmetricDifferenceCard; step++) {
            int bestVm = -1;
            PrVspSolution bestNext = null;

            for (int vm = symmetricDifference.nextSetBit(0); vm >= 0; vm = symmetricDifference.nextSetBit(vm + 1)) {
                PrVspSolution candidate = applyOp(current, guidingSolution, vm);
                if (candidate == null) {
                    continue;
                }

                if (bestNext == null || candidate.objective() < bestNext.objective()) {
                    bestNext = candidate;
                    bestVm = vm;
                }
            }

            if (bestNext == null) {
                break;
            }

            current = bestNext;
            symmetricDifference.clear(bestVm);

            if (current.objective() < best.objective()) {
                best = current;
            }
        }

        return best;
    }

    private PrVspSolution applyOp(PrVspSolution currentSolution, PrVspSolution guidingSolution, int vm) {
        boolean inCurrentSolution = currentSolution.sepBits.get(vm);
        boolean inGuidingSolution = guidingSolution.sepBits.get(vm);

        byte[] part = currentSolution.part.clone();
        int sizeA = currentSolution.sizeA, sizeB = currentSolution.sizeB, sizeC = currentSolution.sizeC;

        if (inCurrentSolution && !inGuidingSolution) {
            byte to = guidingSolution.part[vm];
            if (to == 0 && sizeA >= maxShoreSize) {
                return null;
            }
            if (to == 1 && sizeB >= maxShoreSize) {
                return null;
            }

            part[vm] = to;
            sizeC--;
            if (to == 0) {
                sizeA++;
            } else {
                sizeB++;
            }

            byte opposite = (to == 0) ? (byte)1 : (byte)0;
            for (int u : neighbourhoodData.neighbourhoodMatrix[vm]) {
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

            if (sizeA <= 0 || sizeB <= 0) {
                return null;
            }
        } else if (!inCurrentSolution && inGuidingSolution) {
            byte from = part[vm];
            if (from == 2) {
                return null;
            }
            part[vm] = 2;
            sizeC++;
            if (from == 0) {
                sizeA--;
            } else {
                sizeB--;
            }
            if (sizeA <= 0 || sizeB <= 0) {
                return null;
            }

        } else {
            return null;
        }

        return new PrVspSolution(part, sizeA, sizeB, sizeC);
    }

    public static PrVspSolution tryFlip(PrVspSolution solution, int v,
                                        GraphNeighbourData neighbourhoodData, int maxShoreSize, Random random) {
        byte pv = solution.part[v];

        if (pv == 0 || pv == 1) {
            return moveShoreToC(solution, v);
        } else if (pv == 2) {
            PrVspSolution toA = moveCToShoreWithRepair(solution, v, (byte) 0, neighbourhoodData, maxShoreSize);
            PrVspSolution toB = moveCToShoreWithRepair(solution, v, (byte) 1, neighbourhoodData, maxShoreSize);

            if (toA == null) {
                return toB;
            }
            if (toB == null) {
                return toA;
            }

            if (toA.objective() < toB.objective()) {
                return toA;
            }
            if (toB.objective() < toA.objective()) {
                return toB;
            }
            return random.nextBoolean() ? toA : toB;
        }

        return null;
    }

    private static PrVspSolution moveShoreToC(PrVspSolution solution, int v) {
        byte[] part = solution.part.clone();
        int sizeA = solution.sizeA;
        int sizeB = solution.sizeB;
        int sizeC = solution.sizeC;

        byte from = part[v];
        if (from == 2) {
            return solution;
        }

        if (from == 0 && sizeA <= 1) {
            return null;
        }
        if (from == 1 && sizeB <= 1) {
            return null;
        }

        part[v] = 2;
        sizeC++;
        if (from == 0) {
            sizeA--;
        } else {
            sizeB--;
        }

        return new PrVspSolution(part, sizeA, sizeB, sizeC);
    }

    private static PrVspSolution moveCToShoreWithRepair(PrVspSolution solution, int v, byte toShore,
                                                        GraphNeighbourData neighbourhoodData, int maxShoreSize) {
        byte[] part = solution.part.clone();
        int sizeA = solution.sizeA;
        int sizeB = solution.sizeB;
        int sizeC = solution.sizeC;

        if (part[v] != 2) {
            return null;
        }

        if (toShore == 0 && sizeA >= maxShoreSize) {
            return null;
        }
        if (toShore == 1 && sizeB >= maxShoreSize) {
            return null;
        }

        part[v] = toShore;
        sizeC--;
        if (toShore == 0) {
            sizeA++;
        } else {
            sizeB++;
        }

        byte opposite = (toShore == 0) ? (byte) 1 : (byte) 0;
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

        if (sizeA <= 0 || sizeB <= 0) {
            return null;
        }
        if (sizeA > maxShoreSize || sizeB > maxShoreSize) {
            return null;
        }

        return new PrVspSolution(part, sizeA, sizeB, sizeC);
    }
}
