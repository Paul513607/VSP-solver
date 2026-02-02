package org.vspsolver.util;

import java.util.HashMap;
import java.util.Map;

public class GraphNeighbourData {
    public final int[] ids;
    public final Map<Integer,Integer> indexOfIds;
    public final int[][] neighbourhoodMatrix;
    public final int degreeMaxTop5Avg;

    public GraphNeighbourData(int[] ids, Map<Integer,Integer> indexOfIds, int[][] neighbourhoodMatrix, int degreeMaxTop5Avg) {
        this.ids = ids;
        this.indexOfIds = indexOfIds;
        this.neighbourhoodMatrix = neighbourhoodMatrix;
        this.degreeMaxTop5Avg = degreeMaxTop5Avg;
    }

    public Map<Integer, int[]> getNeighbourhoodAsMap() {
        Map<Integer, int[]> neighbours = new HashMap<>();
        int n = ids.length;
        for (int i = 0; i < n; i++) {
            neighbours.put(ids[i], neighbourhoodMatrix[i]);
        }
        return neighbours;
    }

    public int size() {
        return ids.length;
    }
}
