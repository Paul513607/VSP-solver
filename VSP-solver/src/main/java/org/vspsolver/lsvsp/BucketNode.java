package org.vspsolver.lsvsp;

public class BucketNode {
    public final int v;
    public BucketNode prev;
    public BucketNode next;

    public BucketNode(int v) {
        this.v = v;
    }
}
