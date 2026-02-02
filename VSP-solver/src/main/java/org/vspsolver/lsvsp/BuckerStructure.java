package org.vspsolver.lsvsp;

import java.util.Arrays;

public class BuckerStructure {
    private final int minScore;
    private final int maxScore;
    private final int offset;
    private final BucketNode[] heads;
    private final BucketNode[] nodeOf;
    private final boolean[] present;
    private int currentMaxScore;
    private int count;

    public BuckerStructure(int minScore, int maxScore, int nVertices) {
        if (minScore > maxScore) {
            throw new IllegalArgumentException("minScore > maxScore");
        }
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.offset = -minScore;
        this.heads = new BucketNode[maxScore - minScore + 1];
        this.nodeOf = new BucketNode[nVertices];
        this.present = new boolean[nVertices];
        this.currentMaxScore = minScore;
        this.count = 0;
    }

    public void clear() {
        Arrays.fill(heads, null);
        Arrays.fill(nodeOf, null);
        Arrays.fill(present, false);
        count = 0;
        currentMaxScore = minScore;
    }

    public boolean contains(int v) {
        return present[v];
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public int size() {
        return count;
    }

    public void insert(int v, int score) {
        int idx = score + offset;
        BucketNode node = new BucketNode(v);
        nodeOf[v] = node;
        present[v] = true;

        BucketNode head = heads[idx];
        if (head != null) {
            node.next = head;
            head.prev = node;
        }
        heads[idx] = node;

        if (count == 0 || score > currentMaxScore) {
            currentMaxScore = score;
        }
        count++;
    }

    public void remove(int v, int score) {
        if (!present[v]) {
            return;
        }
        int idx = score + offset;
        BucketNode node = nodeOf[v];
        if (node == null) {
            return;
        }

        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            heads[idx] = node.next;
        }

        if (node.next != null) {
            node.next.prev = node.prev;
        }

        nodeOf[v] = null;
        present[v] = false;
        count--;

        if (count == 0) {
            currentMaxScore = minScore;
            return;
        }
        if (score == currentMaxScore && heads[idx] == null) {
            for (int score1 = currentMaxScore; score1 >= minScore; score1--) {
                BucketNode head = heads[score1 + offset];
                if (head != null) {
                    currentMaxScore = score1; return;
                }
            }
        }
    }

    public void shift(int v, int oldScore, int newScore) {
        if (!present[v]) {
            return;
        }
        remove(v, oldScore);
        insert(v, newScore);
    }

    public int peekMaxVertex() {
        if (count == 0) return -1;
        for (int score = currentMaxScore; score >= minScore; score--) {
            BucketNode head = heads[score + offset];
            if (head != null) {
                currentMaxScore = score;
                return head.v;
            }
        }
        return -1;
    }

    public int getCurrentMaxScore() {
        return currentMaxScore;
    }
}
