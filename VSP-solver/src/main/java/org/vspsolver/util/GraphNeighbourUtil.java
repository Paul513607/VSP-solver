package org.vspsolver.util;

import org.graph4j.Graph;

import java.util.*;

public class GraphNeighbourUtil {
    private GraphNeighbourUtil() {}

    public static GraphNeighbourData build(Graph graph) {
        int[] ids = graph.vertices();
        int n = ids.length;

        Map<Integer, Integer> indexOfVertex = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexOfVertex.put(ids[i], i);
        }

        List<List<Integer>> temp = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            temp.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            int vertexI = ids[i];
            for (int j = i + 1; j < n; j++) {
                int vertexJ = ids[j];
                if (graph.containsEdge(vertexI, vertexJ)) {
                    temp.get(i).add(j);
                    temp.get(j).add(i);
                }
            }
        }

        int[][] neighbourhoodMatrix = new int[n][];
        int[] degrees = new int[n];
        for (int i = 0; i < n; i++) {
            List<Integer> neighbourList = temp.get(i);
            degrees[i] = neighbourList.size();
            int[] arr = new int[neighbourList.size()];
            for (int j = 0; j < neighbourList.size(); j++) {
                arr[j] = neighbourList.get(j);
            }
            neighbourhoodMatrix[i] = arr;
        }

        int degreeMaxTop5Avg = computeDegreeMaxTop5Avg(degrees);

        return new GraphNeighbourData(ids, indexOfVertex, neighbourhoodMatrix, degreeMaxTop5Avg);
    }

    private static int computeDegreeMaxTop5Avg(int[] degrees) {
        int n = degrees.length;

        int[] copy = degrees.clone();
        Arrays.sort(copy);

        int k = Math.max(1, (int)Math.ceil(0.05 * n));

        long sum = 0;
        for (int i = n - k; i < n; i++) {
            sum += copy[i];
        }

        return (int)Math.max(1, Math.round(sum / (double)k));
    }
}
