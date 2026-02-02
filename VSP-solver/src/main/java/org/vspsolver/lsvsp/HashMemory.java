package org.vspsolver.lsvsp;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Random;

public class HashMemory {
    private final int n;
    private final int maxHS;
    private final int maxSLO;

    private final int[] z;
    private final HashEntry[] table;
    private final ArrayDeque<HashEntry> lru;

    public HashMemory(int n, int maxHS, int maxSLO, Random rnd) {
        this.n = n;
        this.maxHS = maxHS;
        this.maxSLO = maxSLO;
        this.z = new int[n];
        for (int i = 0; i < n; i++) z[i] = 1 + rnd.nextInt(131072);

        this.table = new HashEntry[maxHS + 1];
        this.lru = new ArrayDeque<>();
    }

    public int touch(BitSet sepBits, int iter) {
        long key = hash(sepBits);
        int idx = (int) (key % (maxHS + 1));
        int start = idx;

        while (true) {
            HashEntry e = table[idx];
            if (e == null) {
                insert(idx, key, (BitSet) sepBits.clone(), iter);
                return -1;
            }
            if (e.key == key && e.sepBits.equals(sepBits)) {
                int prev = e.lastIter;
                e.lastIter = iter;
                return prev;
            }
            idx++;
            if (idx > maxHS) idx = 0;
            if (idx == start) {
                clear();
                int newIdx = (int) (key % (maxHS + 1));
                insert(newIdx, key, (BitSet) sepBits.clone(), iter);
                return -1;
            }
        }
    }

    public void clear() {
        for (int i = 0; i < table.length; i++) {
            table[i] = null;
        }
        lru.clear();
    }

    private long hash(BitSet sepBits) {
        long sum = 0;
        for (int v = sepBits.nextSetBit(0); v >= 0; v = sepBits.nextSetBit(v + 1)) {
            sum += z[v];
        }
        return sum;
    }

    private void insert(int idx, long key, BitSet sepBits, int iter) {
        HashEntry e = new HashEntry(idx, key, sepBits, iter);
        table[idx] = e;
        lru.addLast(e);

        while (lru.size() > maxSLO) {
            HashEntry old = lru.removeFirst();
            if (table[old.slot] == old) table[old.slot] = null;
        }
    }

    private static class HashEntry {
        final int slot;
        final long key;
        final BitSet sepBits;
        int lastIter;

        HashEntry(int slot, long key, BitSet sepBits, int lastIter) {
            this.slot = slot;
            this.key = key;
            this.sepBits = sepBits;
            this.lastIter = lastIter;
        }
    }
}
