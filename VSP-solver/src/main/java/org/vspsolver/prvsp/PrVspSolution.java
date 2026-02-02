package org.vspsolver.prvsp;

import java.util.BitSet;

public class PrVspSolution {
    public final byte[] part;
    public final int sizeA, sizeB, sizeC;
    public final BitSet sepBits;

    public PrVspSolution(byte[] part, int sizeA, int sizeB, int sizeC) {
        this.part = part;
        this.sizeA = sizeA;
        this.sizeB = sizeB;
        this.sizeC = sizeC;
        this.sepBits = buildSepBits(part);
    }

    public int objective() {
        return sizeC;
    }

    public static BitSet buildSepBits(byte[] part) {
        BitSet bs = new BitSet(part.length);
        for (int i = 0; i < part.length; i++) {
            if (part[i] == 2) bs.set(i);
        }
        return bs;
    }

    public static int sepDistance(PrVspSolution solution1, PrVspSolution solution2) {
        BitSet t = (BitSet)solution1.sepBits.clone();
        t.xor(solution2.sepBits);
        return t.cardinality();
    }
}
