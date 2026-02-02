package org.vspsolver.prvsp;

public class OneSwapMove {
    public final int v;
    public final byte toShore;
    public final boolean swap;
    public final int newObj;

    public OneSwapMove(int v, byte toShore, boolean swap, int newObj) {
        this.v = v;
        this.toShore = toShore;
        this.swap = swap;
        this.newObj = newObj;
    }
}
