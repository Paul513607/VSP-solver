package org.vspsolver.prvsp;

import org.vspsolver.util.GraphNeighbourData;

import java.util.Random;

public class PrVspSolutionUtil {
    public static PrVspSolution randomInitial(GraphNeighbourData neighbourhoodData, int maxShoreSize, Random random) {
        int size = neighbourhoodData.size();
        byte[] part = new byte[size];
        int sizeA = 0;
        int sizeB = 0;
        int sizeC = 0;

        for (int i = 0; i < size; i++) {
            part[i] = random.nextBoolean() ? (byte)0 : (byte)1;
            if (part[i] == 0) {
                sizeA++;
            } else {
                sizeB++;
            }
        }

        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < size && !changed; i++) {
                if (part[i] == 2) {
                    continue;
                }
                for (int j : neighbourhoodData.neighbourhoodMatrix[i]) {
                    if (part[i] == 0 && part[j] == 1) {
                        int pick = random.nextBoolean() ? i : j;
                        if (part[pick] == 0) {
                            sizeA--;
                        } else {
                            sizeB--;
                        }
                        part[pick] = 2; sizeC++;
                        changed = true;
                        break;
                    }
                    if (part[i] == 1 && part[j] == 0) {
                        int pick = random.nextBoolean() ? i : j;
                        if (part[pick] == 0) {
                            sizeA--;
                        } else {
                            sizeB--;
                        }
                        part[pick] = 2; sizeC++;
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        while (sizeA > maxShoreSize) {
            int v = pickRandomWithPart(part, (byte)0, random);
            part[v] = 2; sizeA--; sizeC++;
        }
        while (sizeB > maxShoreSize) {
            int v = pickRandomWithPart(part, (byte)1, random);
            part[v] = 2; sizeB--; sizeC++;
        }

        if (sizeA == 0) {
            int v = pickRandomWithPart(part, (byte)2, random); part[v]=0; sizeA++; sizeC--;
        }
        if (sizeB == 0) {
            int v = pickRandomWithPart(part, (byte)2, random); part[v]=1; sizeB++; sizeC--;
        }

        return new PrVspSolution(part, sizeA, sizeB, sizeC);
    }

    public static int pickRandomWithPart(byte[] part, byte p, Random rnd) {
        for (int tries = 0; tries < 10_000; tries++) {
            int i = rnd.nextInt(part.length);
            if (part[i] == p) {
                return i;
            }
        }
        for (int i = 0; i < part.length; i++) {
            if (part[i] == p) {
                return i;
            }
        }
        return -1;
    }
}
