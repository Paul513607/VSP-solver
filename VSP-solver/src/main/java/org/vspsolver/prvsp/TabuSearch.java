package org.vspsolver.prvsp;

import java.util.*;
import org.vspsolver.util.GraphNeighbourData;

public class TabuSearch {
    private final GraphNeighbourData neighbourhoodData;
    private final int maxShoreSize;
    private final Random random;

    public TabuSearch(GraphNeighbourData neighbourhoodData, int maxShoreSize, Random random) {
        this.neighbourhoodData = neighbourhoodData;
        this.maxShoreSize = maxShoreSize;
        this.random = random;
    }

    public PrVspSolution improve(PrVspSolution start) {
        int n = neighbourhoodData.size();
        byte[] part = start.part.clone();
        int sizeA = start.sizeA;
        int sizeB = start.sizeB;
        int sizeC = start.sizeC;

        int[][] tabuUntil = new int[n][2];
        int iter = 0;

        PrVspSolution best = new PrVspSolution(part.clone(), sizeA, sizeB, sizeC);
        int bestObj = best.objective();

        int cutoff = Math.max(1, (int)Math.round(PrVertexSeparatorAlgorithm.BETA * Math.max(1, sizeC)));
        int noImprove = 0;

        while (noImprove < cutoff) {
            iter++;

            byte forcedShore = -1;
            if (sizeA == 0) {
                forcedShore = 0;
            } else if (sizeB == 0) {
                forcedShore = 1;
            }

            OneSwapMove bestOneSwapMove = findBestMove(part, sizeA, sizeB, sizeC, tabuUntil, iter, bestObj, forcedShore);
            if (bestOneSwapMove == null) {
                break;
            }

            TabuResult tabuResult = applyMove(part, sizeA, sizeB, sizeC, tabuUntil, iter, bestOneSwapMove);
            sizeA = tabuResult.sizeA;
            sizeB = tabuResult.sizeB;
            sizeC = tabuResult.sizeC;

            int curObj = sizeC;
            if (curObj < bestObj) {
                bestObj = curObj;
                best = new PrVspSolution(part.clone(), sizeA, sizeB, sizeC);
                noImprove = 0;
            } else {
                noImprove++;
            }
        }

        return best;
    }

    private OneSwapMove findBestMove(byte[] part, int sizeA, int sizeB, int sizeC,
                                     int[][] tabuUntil, int iter, int bestObj, byte forcedShore) {
        int n = part.length;

        OneSwapMove best = null;

        for (int v = 0; v < n; v++) {
            if (part[v] != 2) {
                continue;
            }

            if (forcedShore == -1 || forcedShore == 0) {
                OneSwapMove m = evalMove(part, sizeA, sizeB, sizeC, tabuUntil, iter, bestObj, v, (byte)0);
                best = better(best, m);
            }
            if (forcedShore == -1 || forcedShore == 1) {
                OneSwapMove m = evalMove(part, sizeA, sizeB, sizeC, tabuUntil, iter, bestObj, v, (byte)1);
                best = better(best, m);
            }
        }

        return best;
    }

    private OneSwapMove better(OneSwapMove a, OneSwapMove b) {
        if (b == null) {
            return a;
        }
        if (a == null) {
            return b;
        }
        if (b.newObj < a.newObj) {
            return b;
        }
        if (b.newObj > a.newObj) {
            return a;
        }
        return random.nextBoolean() ? a : b;
    }

    private OneSwapMove evalMove(byte[] part, int sizeA, int sizeB, int sizeC,
                                 int[][] tabuUntil, int iter, int bestObj,
                                 int v, byte toShore) {
        boolean swap = (toShore == 0 && sizeA == maxShoreSize) || (toShore == 1 && sizeB == maxShoreSize);
        if (!swap) {
            if ((toShore == 0 && sizeA >= maxShoreSize) || (toShore == 1 && sizeB >= maxShoreSize)) {
                return null;
            }
        }

        byte opp = (toShore == 0) ? (byte)1 : (byte)0;
        int pushed = 0;
        for (int u : neighbourhoodData.neighbourhoodMatrix[v]) {
            if (part[u] == opp) pushed++;
        }

        int newSizeA = sizeA;
        int newSizeB = sizeB;
        int newSizeC = sizeC;

        newSizeC -= 1;
        if (toShore == 0) {
            newSizeA += 1;
        } else {
            newSizeB += 1;
        }

        if (swap) {
            newSizeC += 1;
            if (toShore == 0) {
                newSizeA -= 1;
            } else {
                newSizeB -= 1;
            }
        }

        newSizeC += pushed;
        if (opp == 0) {
            newSizeA -= pushed;
        } else {
            newSizeB -= pushed;
        }

        if (newSizeA <= 0 || newSizeB <= 0) {
            return null;
        }
        if (newSizeA > maxShoreSize || newSizeB > maxShoreSize) {
            return null;
        }

        boolean tabu = iter < tabuUntil[v][toShore];
        boolean aspiration = newSizeC < bestObj;
        if (tabu && !aspiration) {
            return null;
        }

        return new OneSwapMove(v, toShore, swap, newSizeC);
    }

    private TabuResult applyMove(byte[] part, int sizeA, int sizeB, int sizeC,
                                 int[][] tabuUntil, int iter, OneSwapMove oneSwapMove) {
        int v = oneSwapMove.v;
        byte to = oneSwapMove.toShore;
        byte opp = (to == 0) ? (byte)1 : (byte)0;

        int swappedVertex = -1;
        if (oneSwapMove.swap) {
            swappedVertex = pickAnyFromShore(part, to);
            if (swappedVertex < 0) {
                return new TabuResult(sizeA, sizeB, sizeC);
            }
            part[swappedVertex] = 2;
            if (to == 0) {
                sizeA--;
            } else {
                sizeB--;
            }
            sizeC++;
        }

        part[v] = to;
        sizeC--;
        if (to == 0) {
            sizeA++;
        } else {
            sizeB++;
        }

        for (int u : neighbourhoodData.neighbourhoodMatrix[v]) {
            if (part[u] == opp) {
                part[u] = 2;
                sizeC++;
                if (opp == 0) {
                    sizeA--;
                } else {
                    sizeB--;
                }
            }
        }

        int tenure = tabuTenure(sizeC);
        tabuUntil[v][to] = iter + tenure;
        if (oneSwapMove.swap && swappedVertex >= 0) {
            tabuUntil[swappedVertex][to] = iter + tenure;
        }

        return new TabuResult(sizeA, sizeB, sizeC);
    }

    private int tabuTenure(int cSize) {
        int halfC = Math.max(1, cSize / 2);
        int degreeMaxTop5Avg = neighbourhoodData.degreeMaxTop5Avg;
        int term1 = Math.min(degreeMaxTop5Avg, halfC);
        int bound = Math.max(1, (int)Math.floor(PrVertexSeparatorAlgorithm.ALPHA * degreeMaxTop5Avg));
        int term2 = Math.min(random.nextInt(bound + 1), halfC);
        return Math.max(1, term1 + term2);
    }

    private int pickAnyFromShore(byte[] part, byte shore) {
        for (int tries = 0; tries < 10_000; tries++) {
            int i = random.nextInt(part.length);
            if (part[i] == shore) {
                return i;
            }
        }
        for (int i = 0; i < part.length; i++) {
            if (part[i] == shore) {
                return i;
            }
        }
        return -1;
    }
}
